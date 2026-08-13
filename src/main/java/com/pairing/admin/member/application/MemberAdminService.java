package com.pairing.admin.member.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.MemberStatusFilter;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.domain.SignupMethod;
import com.pairing.admin.member.exception.MemberErrorCode;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaEntity;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaRepository;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.ListRow;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.ProfileRow;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.ReviewStatsRow;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.SkillRow;
import com.pairing.admin.member.infrastructure.redis.BackendSuspensionGateway;
import com.pairing.admin.member.presentation.api.response.MemberDetailResponse;
import com.pairing.admin.member.presentation.api.response.MemberStatsResponse;
import com.pairing.admin.member.presentation.api.response.MemberSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 회원 관리. (관리자 &gt; 회원 관리)
 *
 * <p>백엔드와 같은 account 테이블을 쓴다. 여기서 회원을 정지하면 백엔드 로그인에 즉시 반영된다.
 * 반대로 말하면, 이 서비스의 버그가 서비스 전체 로그인을 막을 수 있다는 뜻이기도 하다.
 * 그래서 엔티티에 세터를 열지 않고 {@code suspend/releaseSuspension} 같은 의도가 드러나는 메서드만 쓴다.
 *
 * <p><b>정지는 두 곳에 쓴다.</b> 역할이 다르다.
 * <ul>
 *   <li>Redis {@code SUSPEND:{id}} — 백엔드가 로그인을 막을 때 보는 <b>원본</b>이다.
 *       ({@code AccountSuspensionPort})</li>
 *   <li>{@code account.suspended_at} — 목록 필터와 요약 카드를 SQL 로 세기 위한 <b>사본</b>이다.
 *       Redis 키로는 "정지 회원만 보기" 를 만들 수 없다.</li>
 * </ul>
 * {@code account.status} 는 <b>건드리지 않는다.</b> 백엔드 enum 에 SUSPENDED 가 없어서
 * 그 값을 쓰면 백엔드가 해당 계정을 읽는 순간 터진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAdminService {

    private final AccountJpaRepository accountJpaRepository;
    private final MemberAdminQueryRepository memberAdminQueryRepository;
    private final BackendSuspensionGateway suspensionGateway;

    // ==================================================================
    // 조회
    // ==================================================================

    /**
     * 회원 목록.
     *
     * <p>정렬은 쿼리에 최신 가입순으로 고정되어 있다. {@code pageable} 에서는 페이지 번호와
     * 크기만 쓴다. 네이티브 쿼리에 정렬을 문자열로 이어 붙이면 SQL 주입 경로가 되기 때문이다.
     */
    @Transactional(readOnly = true)
    public PageResponse<MemberSummaryResponse> search(Role role,
                                                      MemberStatusFilter status,
                                                      SignupMethod signupMethod,
                                                      String keyword,
                                                      Pageable pageable) {

        String roleParam = role == null ? null : role.name();
        String statusParam = status == null ? null : status.name();
        String methodParam = signupMethod == null ? null : signupMethod.name();
        String keywordParam = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        List<MemberSummaryResponse> content = memberAdminQueryRepository
                .findPage(roleParam, statusParam, methodParam, keywordParam,
                        pageable.getPageSize(), pageable.getOffset())
                .stream()
                .map(MemberSummaryResponse::from)
                .toList();

        long total = memberAdminQueryRepository.countPage(roleParam, statusParam, methodParam, keywordParam);

        return PageResponse.from(new PageImpl<>(content, pageable, total));
    }

    /** 목록 상단 요약 카드 6개. */
    @Transactional(readOnly = true)
    public MemberStatsResponse findStats() {
        return MemberStatsResponse.from(memberAdminQueryRepository.findSummary());
    }

    @Transactional(readOnly = true)
    public MemberDetailResponse findDetail(Long accountId) {
        return buildDetail(loadMember(accountId));
    }

    // ==================================================================
    // 정지 / 해제
    // ==================================================================

    /**
     * 회원을 정지한다. 새 로그인이 막히고 이미 로그인된 세션도 즉시 끊긴다.
     *
     * <p>DB 를 먼저 쓰고 Redis 를 나중에 쓴다. 순서에 이유가 있다. Redis 쓰기가 실패하면
     * 예외가 올라와 트랜잭션이 롤백되므로, <b>"관리자 화면에는 정지로 보이는데 회원은 멀쩡히
     * 쓰고 있는"</b> 상태가 남지 않는다. 반대 순서로 하면 그 상태를 만들 수 있다.
     *
     * <p>{@code flush()} 로 DB 반영을 Redis 쓰기 전에 확정한다. 이렇게 해야 제약 위반 같은
     * DB 오류가 Redis 를 건드리기 전에 드러난다.
     *
     * <p>남는 위험이 하나 있다. Redis 의 마커 생성은 성공했는데 뒤이은 세션 삭제가 실패하면
     * 트랜잭션은 롤백되고 마커만 남는다. 이때 회원은 로그인이 막히지만 관리자 화면에는 정지로
     * 보이지 않는다. 반대(화면은 정지인데 실제로는 안 막힘)보다 안전한 방향이라 이렇게 뒀다.
     */
    @Transactional
    public MemberDetailResponse suspend(Long accountId, String reason, Long actorAdminId) {
        AccountJpaEntity member = loadModifiableMember(accountId);

        if (member.isSuspended()) {
            throw new BusinessException(MemberErrorCode.ALREADY_IN_STATUS, "이미 정지된 회원입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        member.suspend(reason, now);
        accountJpaRepository.flush();

        suspensionGateway.suspend(accountId, actorAdminId, now);

        // 누가 무엇을 했는지 남긴다. 감사 로그 테이블이 생기면 이 자리에 기록을 추가한다.
        log.info("[회원 정지] targetAccountId={}, actorAdminId={}, reason={}", accountId, actorAdminId, reason);

        return buildDetail(member);
    }

    /**
     * 정지를 해제한다. 비밀번호 실패 횟수도 함께 초기화된다.
     *
     * <p>여기서도 Redis 실패는 롤백으로 이어진다. 마커가 남은 채 DB 만 풀리면 관리자는
     * 해제됐다고 보는데 회원은 계속 로그인하지 못한다.
     */
    @Transactional
    public MemberDetailResponse releaseSuspension(Long accountId, Long actorAdminId) {
        AccountJpaEntity member = loadModifiableMember(accountId);

        if (!member.isSuspended()) {
            throw new BusinessException(MemberErrorCode.ALREADY_IN_STATUS, "정지 상태가 아닌 회원입니다.");
        }

        member.releaseSuspension();
        accountJpaRepository.flush();

        suspensionGateway.release(accountId);

        log.info("[회원 정지 해제] targetAccountId={}, actorAdminId={}", accountId, actorAdminId);

        return buildDetail(member);
    }

    // ==================================================================

    /**
     * 상세 응답을 조립한다.
     *
     * <p>프로필과 활동 현황은 계정 한 건마다 집계 쿼리를 돈다. 상세 화면은 한 번에 한 명만
     * 열기 때문에 감당할 만하지만, 목록에서 사람 수만큼 부르면 안 된다.
     * 목록은 별도의 한 방 쿼리({@link ListRow})를 쓴다.
     */
    private MemberDetailResponse buildDetail(AccountJpaEntity member) {
        Long accountId = member.getId();

        ProfileRow profile = memberAdminQueryRepository.findProfile(accountId).orElse(null);

        // 기술 스택은 프리랜서만 있다. 클라이언트에게는 빈 목록이 오고, 응답에서도 버려진다.
        List<SkillRow> skills = memberAdminQueryRepository.findSkills(accountId);

        var projects = memberAdminQueryRepository.findProjectActivity(accountId);
        BigDecimal paid = memberAdminQueryRepository.sumPaidAmount(accountId);
        ReviewStatsRow reviews = memberAdminQueryRepository.findReviewStats(accountId);

        MemberDetailResponse.Activity activity = new MemberDetailResponse.Activity(
                projects.getInProgressProjects(),
                projects.getCompletedProjects(),
                projects.getCanceledProjects(),
                // 정산 이력이 없으면 SUM 이 null 이다. 화면에서 0원으로 보이도록 바꿔 준다.
                paid == null ? BigDecimal.ZERO : paid,
                reviews.getReviewCount(),
                // 평균은 리뷰가 없으면 null 이다. 0.0 으로 바꾸면 "별점 0점" 과 구분이 안 되므로 그대로 둔다.
                reviews.getAverageScore());

        return MemberDetailResponse.from(member, profile, skills, activity);
    }

    /**
     * 회원 한 명을 읽는다.
     *
     * <p><b>{@code deleted_at} 으로 거르지 않는다.</b> 백엔드의 탈퇴는 상태를 WITHDRAWN 으로
     * 바꾸면서 {@code deleted_at} 도 채운다. 여기서 걸러 버리면 탈퇴 회원의 상세를 아예 볼 수 없다.
     */
    private AccountJpaEntity loadMember(Long accountId) {
        return accountJpaRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 상태를 바꿀 수 있는 회원만 가져온다.
     *
     * <p>관리자 계정과 탈퇴 회원은 제외한다. 관리자를 이 화면에서 정지할 수 있으면
     * 서로 정지시켜 아무도 로그인하지 못하는 상태를 만들 수 있다.
     */
    private AccountJpaEntity loadModifiableMember(Long accountId) {
        AccountJpaEntity member = loadMember(accountId);

        if (member.getRole() == Role.ADMIN) {
            throw new BusinessException(MemberErrorCode.CANNOT_MODIFY_ADMIN);
        }

        if (member.getStatus() == AccountStatus.WITHDRAWN) {
            throw new BusinessException(MemberErrorCode.WITHDRAWN_MEMBER);
        }

        return member;
    }
}
