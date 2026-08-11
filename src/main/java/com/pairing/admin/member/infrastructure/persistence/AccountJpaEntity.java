package com.pairing.admin.member.infrastructure.persistence;

import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.domain.SignupType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * account 테이블 매핑. <b>백엔드와 공유하는 테이블</b>이다.
 *
 * <p>주의할 점 세 가지.
 * <ol>
 *   <li>이 서버는 계정을 <b>만들지 않는다.</b> 조회와 상태 변경만 한다.
 *       가입 로직은 백엔드에만 있어야 중복 검증·중복 정책이 생기지 않는다.</li>
 *   <li>created_at / updated_at 은 DB 기본값과 트리거(set_updated_at)가 채운다.
 *       읽기 전용으로 매핑해서 애플리케이션이 값을 덮어쓰지 못하게 한다.</li>
 *   <li>컬럼을 추가·변경하려면 백엔드의 db/init/02-create-schema.sql 을 먼저 고친다.
 *       이 서버는 ddl-auto=validate 라 스키마를 바꾸지 않는다.</li>
 * </ol>
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "signup_type", nullable = false, length = 20)
    private SignupType signupType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "is_temp_password", nullable = false)
    private boolean tempPassword;

    @Column(name = "password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "suspend_reason", length = 500)
    private String suspendReason;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdraw_reason", length = 500)
    private String withdrawReason;

    @Column(name = "rejoin_available_at")
    private LocalDateTime rejoinAvailableAt;

    /** DB 기본값이 채운다. 관리자 목록의 "가입일" 이 이 값이다. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** DB 트리거(set_updated_at)가 채운다. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ------------------------------------------------------------------
    // 상태 변경. 관리자가 회원에게 할 수 있는 일만 메서드로 열어 둔다.
    //
    // 세터를 전부 열면 관리자 서버에서 회원의 이메일·역할·비밀번호까지 바꿀 수 있게 된다.
    // 그건 백엔드(회원 본인의 요청)가 할 일이지 관리 화면이 할 일이 아니다.
    // 계정 생성·비밀번호 변경 메서드를 일부러 두지 않은 것도 같은 이유다.
    // ------------------------------------------------------------------

    /** 계정을 잠근다. (관리자 화면의 "정지") */
    public void lock(String reason) {
        this.status = AccountStatus.LOCKED;
        this.suspendReason = reason;
        this.lockedAt = LocalDateTime.now();
    }

    /** 잠금을 푼다. 실패 횟수도 함께 초기화해야 곧바로 다시 잠기지 않는다. */
    public void unlock() {
        this.status = AccountStatus.ACTIVE;
        this.suspendReason = null;
        this.lockedAt = null;
        this.loginFailCount = 0;
    }
}
