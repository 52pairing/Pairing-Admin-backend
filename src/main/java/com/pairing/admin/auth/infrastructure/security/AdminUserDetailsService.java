package com.pairing.admin.auth.infrastructure.security;

import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaEntity;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaRepository;
import com.pairing.admin.global.security.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디로 관리자 계정을 찾아 온다.
 *
 * <p>조회 대상은 {@code admin_user} 테이블뿐이다. 회원(account) 테이블은 쳐다보지도 않는다.
 * 테이블이 분리되어 있으므로 "role 이 ADMIN 인지" 같은 조건을 따로 걸 필요가 없다.
 * 여기 있는 행은 전부 관리자다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserJpaRepository adminUserJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        AdminUserJpaEntity admin = adminUserJpaRepository.findByUsername(username)
                .orElseThrow(() -> {
                    // 계정이 없는 것과 비밀번호가 틀린 것을 응답에서 구분하지 않는다.
                    // 구분하면 어떤 아이디가 존재하는지 알려주는 셈이 된다.
                    log.warn("[로그인 실패] 존재하지 않는 관리자 아이디: {}", username);
                    return new UsernameNotFoundException("관리자 계정을 찾을 수 없습니다.");
                });

        return new AdminPrincipal(
                admin.getId(),
                admin.getUsername(),
                admin.getPasswordHash(),
                !admin.isLocked()   // accountNonLocked
        );
    }
}
