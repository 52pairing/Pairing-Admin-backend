package com.pairing.admin.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 관리자 계정 전용 저장소.
 *
 * <p>{@code admin_user} 테이블만 다룬다. 회원(account) 테이블은
 * {@code member.infrastructure.persistence.AccountJpaRepository} 가 따로 담당한다.
 */
public interface AdminUserJpaRepository extends JpaRepository<AdminUserJpaEntity, Long> {

    Optional<AdminUserJpaEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
