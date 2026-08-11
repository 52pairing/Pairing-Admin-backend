package com.pairing.admin.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * admin_user 테이블 매핑. <b>관리자 서버만 쓰는 전용 테이블</b>이다.
 *
 * <p>회원(account)과 완전히 분리했다. 같은 테이블에 role 로 섞어 두면
 * <ul>
 *   <li>회원 관리 화면의 실수 하나가 관리자 계정까지 건드릴 수 있고,</li>
 *   <li>회원가입·소셜로그인·탈퇴 같은 서비스 로직이 관리자 행에도 흘러들 수 있으며,</li>
 *   <li>백엔드가 account 스키마를 바꿀 때마다 관리자 로그인이 영향을 받는다.</li>
 * </ul>
 * 관리자는 사람 수도 적고 수명도 다르다. 테이블을 나누는 편이 서로를 지켜 준다.
 *
 * <p>필요한 것은 아이디와 비밀번호뿐이다. 나머지 컬럼은 잠금·감사에 필요한 최소한이다.
 *
 * <p>created_at / updated_at 은 이 엔티티가 직접 채운다.
 * account 테이블처럼 DB 트리거(set_updated_at)에 의존하지 않는다.
 * 이 테이블은 관리자 서버가 단독으로 소유하므로 관리 주체를 애플리케이션 한 곳으로 두는 편이 단순하다.
 */
@Entity
@Table(name = "admin_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 아이디. 이메일이 아니어도 된다. */
    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    /** BCrypt 해시. 60자 고정이다. */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    /** 값이 있으면 잠긴 상태다. 별도 status 컬럼을 두지 않고 이 하나로 표현한다. */
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private AdminUserJpaEntity(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.loginFailCount = 0;
    }

    public static AdminUserJpaEntity create(String username, String passwordHash) {
        return new AdminUserJpaEntity(username, passwordHash);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ------------------------------------------------------------------

    public boolean isLocked() {
        return lockedAt != null;
    }

    public void onLoginSuccess() {
        this.loginFailCount = 0;
        this.lastLoginAt = LocalDateTime.now();
    }

    /**
     * 로그인 실패 처리. 상한에 도달하면 잠근다.
     *
     * @return 이번 실패로 잠겼으면 true
     */
    public boolean onLoginFailure(int failMax) {
        this.loginFailCount++;

        if (this.loginFailCount >= failMax && !isLocked()) {
            this.lockedAt = LocalDateTime.now();
            return true;
        }

        return false;
    }

    /** 잠금 해제. 실패 횟수도 함께 초기화해야 곧바로 다시 잠기지 않는다. */
    public void unlock() {
        this.lockedAt = null;
        this.loginFailCount = 0;
    }

    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }
}
