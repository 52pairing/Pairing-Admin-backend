package com.pairing.admin.auth.application;

import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 성공/실패 횟수를 admin_user 테이블에 기록한다.
 *
 * <p>{@code AdminAuthService} 와 분리한 이유가 있다. 로그인 실패는 예외를 던져서 끝나는데,
 * 같은 트랜잭션 안에서 실패 횟수를 올리면 그 예외로 <b>증가분까지 롤백</b>된다.
 * 별도 빈으로 두면 각 메서드가 자기 트랜잭션에서 커밋되고 끝나므로 횟수가 남는다.
 * (같은 클래스 안의 메서드 호출은 프록시를 타지 않아 {@code @Transactional} 이 걸리지 않는다)
 */
@Slf4j
@Component
public class LoginAttemptRecorder {

    private final AdminUserJpaRepository adminUserJpaRepository;
    private final int loginFailMax;

    public LoginAttemptRecorder(AdminUserJpaRepository adminUserJpaRepository,
                                @Value("${app.security.login-fail-max:5}") int loginFailMax) {
        this.adminUserJpaRepository = adminUserJpaRepository;
        this.loginFailMax = loginFailMax;
    }

    @Transactional
    public void recordSuccess(String username) {
        adminUserJpaRepository.findByUsername(username)
                .ifPresent(admin -> {
                    admin.onLoginSuccess();
                    log.info("[로그인 성공] adminId={}, username={}", admin.getId(), username);
                });
    }

    /**
     * 실패 횟수를 1 올리고, 상한에 닿으면 계정을 잠근다.
     *
     * <p>존재하지 않는 아이디면 아무 것도 하지 않는다. 없는 계정에 기록을 남기면
     * 그 자체가 "이 아이디는 없다" 는 신호가 될 수 있어 조용히 넘어간다.
     */
    @Transactional
    public void recordFailure(String username) {
        adminUserJpaRepository.findByUsername(username)
                .ifPresent(admin -> {
                    boolean lockedNow = admin.onLoginFailure(loginFailMax);

                    if (lockedNow) {
                        log.warn("[계정 잠금] 비밀번호 {}회 연속 실패: adminId={}", loginFailMax, admin.getId());
                    } else {
                        log.warn("[로그인 실패] adminId={}, failCount={}/{}",
                                admin.getId(), admin.getLoginFailCount(), loginFailMax);
                    }
                });
    }
}
