package com.pairing.admin.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 세션에 담기는 로그인 관리자 정보.
 *
 * <p>{@code admin_user} 테이블의 계정이다. 회원(account)과는 아무 관계가 없다.
 *
 * <p>세션에 직렬화되어 들어가므로 {@link Serializable} 이어야 하고, 필드는 최소한으로 유지한다.
 * 엔티티를 통째로 넣으면 세션이 비대해지고, 세션 값과 DB 값이 어긋나기 시작한다.
 */
@Getter
public class AdminPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    /** admin_user.id */
    private final Long adminId;

    /** 로그인 아이디 */
    private final String username;

    /** 인증에만 쓰이는 값. 인증이 끝나면 {@link #eraseCredentials()} 로 지운다. */
    private transient String passwordHash;

    private final boolean accountNonLocked;

    public AdminPrincipal(Long adminId, String username, String passwordHash, boolean accountNonLocked) {
        this.adminId = adminId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.accountNonLocked = accountNonLocked;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 이 서버는 관리자만 접근한다. 권한을 더 잘게 나눌 일이 생기면
        // (예: 읽기 전용 운영자) 여기서 여러 개를 돌려주고 @PreAuthorize 로 구분한다.
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** admin_user 에는 비활성 상태가 없다. 잠금(locked_at)만으로 충분하다. */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 인증 성공 후 세션에 비밀번호 해시가 남지 않도록 지운다. */
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
