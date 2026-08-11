package com.pairing.admin.member.application;

import com.pairing.admin.global.common.api.response.PageResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.exception.MemberErrorCode;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaEntity;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaRepository;
import com.pairing.admin.member.infrastructure.persistence.AccountSpecs;
import com.pairing.admin.member.presentation.api.response.MemberDetailResponse;
import com.pairing.admin.member.presentation.api.response.MemberSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 관리. (관리자 &gt; 회원 관리)
 *
 * <p>백엔드와 같은 account 테이블을 쓴다. 여기서 상태를 바꾸면 백엔드 로그인에 즉시 반영된다.
 * 반대로 말하면, 이 서비스의 버그가 서비스 전체 로그인을 막을 수 있다는 뜻이기도 하다.
 * 그래서 엔티티에 세터를 열지 않고 {@code lock/unlock} 같은 의도가 드러나는 메서드만 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAdminService {

    private final AccountJpaRepository accountJpaRepository;

    @Transactional(readOnly = true)
    public PageResponse<MemberSummaryResponse> search(Role role, AccountStatus status,
                                                      String keyword, Pageable pageable) {

        // and(null) 은 그 조건을 무시한다. 덕분에 if 문 없이 조건을 이어 붙일 수 있다.
        Specification<AccountJpaEntity> spec = AccountSpecs.notDeleted()
                .and(AccountSpecs.roleEquals(role))
                .and(AccountSpecs.statusEquals(status))
                .and(AccountSpecs.keywordContains(keyword));

        Page<AccountJpaEntity> page = accountJpaRepository.findAll(spec, pageable);

        return PageResponse.from(page, MemberSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public MemberDetailResponse findDetail(Long accountId) {
        return MemberDetailResponse.from(loadMember(accountId));
    }

    /** 회원을 정지(잠금)한다. */
    @Transactional
    public MemberDetailResponse lock(Long accountId, String reason, Long actorAdminId) {
        AccountJpaEntity member = loadModifiableMember(accountId);

        if (member.getStatus() == AccountStatus.LOCKED) {
            throw new BusinessException(MemberErrorCode.ALREADY_IN_STATUS, "이미 정지된 회원입니다.");
        }

        member.lock(reason);

        // 누가 무엇을 했는지 남긴다. 감사 로그 테이블이 생기면 이 자리에 기록을 추가한다.
        log.info("[회원 정지] targetAccountId={}, actorAdminId={}, reason={}",
                accountId, actorAdminId, reason);

        return MemberDetailResponse.from(member);
    }

    /** 정지를 해제한다. 실패 횟수도 함께 초기화된다. */
    @Transactional
    public MemberDetailResponse unlock(Long accountId, Long actorAdminId) {
        AccountJpaEntity member = loadModifiableMember(accountId);

        if (member.getStatus() == AccountStatus.ACTIVE) {
            throw new BusinessException(MemberErrorCode.ALREADY_IN_STATUS, "이미 정상 상태인 회원입니다.");
        }

        member.unlock();

        log.info("[회원 정지 해제] targetAccountId={}, actorAdminId={}", accountId, actorAdminId);

        return MemberDetailResponse.from(member);
    }

    // ------------------------------------------------------------------

    private AccountJpaEntity loadMember(Long accountId) {
        return accountJpaRepository.findByIdAndDeletedAtIsNull(accountId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 상태를 바꿀 수 있는 회원만 가져온다.
     *
     * <p>관리자 계정과 탈퇴 회원은 제외한다. 관리자를 이 화면에서 잠글 수 있으면
     * 서로 잠가서 아무도 로그인하지 못하는 상태를 만들 수 있다.
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
