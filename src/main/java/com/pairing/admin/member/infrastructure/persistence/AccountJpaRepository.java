package com.pairing.admin.member.infrastructure.persistence;

import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * account 테이블 접근.
 *
 * <p>목록 검색은 {@link JpaSpecificationExecutor} 로 처리한다.
 * 관리자 화면은 역할·상태·키워드를 자유롭게 조합하는데, {@code @Query} 에
 * {@code (:role IS NULL OR ...)} 를 늘어놓으면 조건이 늘 때마다 쿼리가 읽기 어려워지고
 * PostgreSQL에서 파라미터 타입 추론이 실패하는 경우도 있다. 조건 조립은 {@link AccountSpecs} 참고.
 */
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long>,
        JpaSpecificationExecutor<AccountJpaEntity> {

    /**
     * 로그인용 조회.
     *
     * <p>account 의 유니크는 (email, role) 복합이다. 한 사람이 클라이언트와 프리랜서로
     * 각각 가입할 수 있기 때문이다. 따라서 이메일만으로 찾으면 여러 건이 나올 수 있고,
     * 역할까지 함께 걸어야 한 건으로 확정된다.
     */
    Optional<AccountJpaEntity> findByEmailAndRoleAndDeletedAtIsNull(String email, Role role);

    Optional<AccountJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    // ------------------------------------------------------------------
    // 대시보드 집계
    // ------------------------------------------------------------------

    long countByDeletedAtIsNull();

    long countByRoleAndDeletedAtIsNull(Role role);

    long countByStatusAndDeletedAtIsNull(AccountStatus status);

    /** 특정 시각 이후 가입자 수. "오늘 가입" 은 오늘 00:00 을 넘겨서 쓴다. */
    long countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(LocalDateTime from);
}
