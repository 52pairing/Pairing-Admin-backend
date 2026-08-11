package com.pairing.admin.auth.application;

import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaEntity;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 관리자 계정 부트스트랩.
 *
 * <p>{@code admin_user} 테이블이 비어 있으면 아무도 로그인할 수 없어서 관리 화면을 열 수조차 없다.
 * 그렇다고 SQL에 BCrypt 해시를 직접 적으려면 해시를 따로 만들어야 해서 번거롭다.
 * 이 컴포넌트는 환경변수로 받은 평문 비밀번호를 애플리케이션이 직접 인코딩해 계정을 만든다.
 *
 * <p><b>기본값은 비활성이다.</b> 켜려면 아래를 주입한다.
 * <pre>
 *   ADMIN_BOOTSTRAP_ENABLED=true
 *   ADMIN_BOOTSTRAP_USERNAME=admin
 *   ADMIN_BOOTSTRAP_PASSWORD=...
 * </pre>
 *
 * <p>계정이 만들어지면 <b>환경변수를 지우고 재배포</b>하는 것을 권한다.
 * 평문 비밀번호가 계속 환경에 남아 있을 이유가 없다.
 * 이미 같은 아이디가 있으면 아무 것도 하지 않으므로, 비밀번호 초기화 용도로는 쓸 수 없다.
 * (비밀번호를 잊었다면 이 계정을 지우고 다시 만들거나, DB에서 해시를 직접 교체한다)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.bootstrap.admin.enabled", havingValue = "true")
public class AdminAccountInitializer {

    private final AdminUserJpaRepository adminUserJpaRepository;
    private final PasswordEncoder passwordEncoder;

    private final String username;
    private final String password;

    public AdminAccountInitializer(AdminUserJpaRepository adminUserJpaRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.bootstrap.admin.username:}") String username,
                                   @Value("${app.bootstrap.admin.password:}") String password) {
        this.adminUserJpaRepository = adminUserJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    /**
     * 기동이 끝난 뒤 실행한다.
     *
     * <p>여기서 예외를 던져 애플리케이션을 죽이지 않는다. 부트스트랩은 편의 기능이고,
     * 실패하더라도 이미 있는 계정으로 로그인하면 되기 때문이다. 대신 로그를 크게 남긴다.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createAdminIfAbsent() {
        if (username.isBlank() || password.isBlank()) {
            log.warn("[관리자 부트스트랩] ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD 가 비어 있어 건너뜁니다.");
            return;
        }

        if (adminUserJpaRepository.existsByUsername(username)) {
            log.info("[관리자 부트스트랩] 이미 존재하는 아이디입니다. 건너뜁니다. username={}", username);
            return;
        }

        adminUserJpaRepository.save(
                AdminUserJpaEntity.create(username, passwordEncoder.encode(password)));

        log.warn("""

                ================================================================
                 관리자 계정을 생성했습니다. username={}
                 로그인한 뒤 비밀번호를 변경하고, ADMIN_BOOTSTRAP_* 환경변수를 제거하세요.
                ================================================================
                """, username);
    }
}
