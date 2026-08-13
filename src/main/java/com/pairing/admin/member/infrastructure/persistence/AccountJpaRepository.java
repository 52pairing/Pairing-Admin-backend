package com.pairing.admin.member.infrastructure.persistence;

import com.pairing.admin.member.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * account 테이블 접근. <b>쓰기(정지·해제)와 단건 조회</b>를 맡는다.
 *
 * <p>회원 목록·요약·상세의 조회 쿼리는 여기 없다. 화면 한 줄에 필요한 값이 여러 테이블에
 * 흩어져 있어 {@link MemberAdminQueryRepository} 의 네이티브 쿼리로 따로 읽는다.
 */
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {

    /**
     * 로그인용 조회.
     *
     * <p>account 의 유니크는 (email, role) 복합이다. 한 사람이 클라이언트와 프리랜서로
     * 각각 가입할 수 있기 때문이다. 따라서 이메일만으로 찾으면 여러 건이 나올 수 있고,
     * 역할까지 함께 걸어야 한 건으로 확정된다.
     */
    Optional<AccountJpaEntity> findByEmailAndRoleAndDeletedAtIsNull(String email, Role role);

    // ------------------------------------------------------------------
    // 대시보드 집계
    //
    // 회원 관리 화면의 요약 카드와는 다른 지표다. 그쪽은 여섯 값을 한 쿼리로 내는
    // MemberAdminQueryRepository.findSummary() 를 쓴다.
    // ------------------------------------------------------------------

    long countByDeletedAtIsNull();

    long countByRoleAndDeletedAtIsNull(Role role);

    /**
     * 정지 회원 수.
     *
     * <p>{@code status} 로 세지 않는다. 정지는 상태값이 아니라 {@code suspended_at} 으로
     * 표현하기 때문이다. (백엔드 AccountStatus enum 에 SUSPENDED 가 없다)
     *
     * <p>{@code deleted_at} 은 보지 않는다. 탈퇴 회원은 정지 대상이 될 수 없어서
     * 정지된 채 탈퇴한 계정이 생기지 않는다.
     */
    long countBySuspendedAtIsNotNull();

    /** 특정 시각 이후 가입자 수. "오늘 가입" 은 오늘 00:00 을 넘겨서 쓴다. */
    long countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(LocalDateTime from);
}
