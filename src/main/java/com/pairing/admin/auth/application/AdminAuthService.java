package com.pairing.admin.auth.application;

import com.pairing.admin.auth.exception.AuthErrorCode;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaEntity;
import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaRepository;
import com.pairing.admin.auth.presentation.api.response.AdminMeResponse;
import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.global.exception.GlobalErrorCode;
import com.pairing.admin.global.security.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인/로그아웃/비밀번호 변경.
 *
 * <p>대상은 {@code admin_user} 테이블이다. 회원(account)과는 완전히 분리되어 있어
 * 여기서 무엇을 하든 서비스 사용자 계정에는 영향이 없다.
 *
 * <p>폼 로그인 필터 대신 이 서비스가 직접 인증을 처리한다.
 * 응답을 {@code ApiResponse} 형태로 통일하고, 실패 사유별로 에러 코드를 나누기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AdminUserJpaRepository adminUserJpaRepository;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final PasswordEncoder passwordEncoder;

    /** application.yaml 의 server.servlet.session.cookie.name 과 같은 값이어야 로그아웃 시 쿠키가 지워진다. */
    @Value("${server.servlet.session.cookie.name:ADMIN_SESSION}")
    private String sessionCookieName;

    /**
     * 로그인. 성공하면 세션을 만들고 SecurityContext 를 저장한다.
     *
     * <p>이 메서드에는 {@code @Transactional} 을 걸지 않는다.
     * 실패 시 횟수 기록이 함께 롤백되면 안 되기 때문이다. ({@link LoginAttemptRecorder} 주석 참고)
     */
    public AdminMeResponse login(String username, String rawPassword,
                                 HttpServletRequest request, HttpServletResponse response) {

        Authentication authentication = authenticate(username, rawPassword);

        // 세션 고정 공격 방어. 로그인 전에 공격자가 심어 둔 세션 ID가 그대로 인증 상태를 얻는 것을 막는다.
        // 기존 세션이 없으면 changeSessionId() 가 IllegalStateException 을 던지므로 먼저 만든다.
        HttpSession session = request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 스프링 시큐리티 6부터는 이 호출이 없으면 세션에 인증 정보가 저장되지 않는다.
        // (다음 요청에서 곧바로 401 이 되는 흔한 원인)
        securityContextRepository.saveContext(context, request, response);

        AdminPrincipal principal = (AdminPrincipal) authentication.getPrincipal();
        principal.eraseCredentials();

        loginAttemptRecorder.recordSuccess(username);

        log.info("[로그인] adminId={}, sessionId={}", principal.getAdminId(), session.getId());

        // 세션에 담긴 값이 아니라 방금 갱신된 DB 값을 돌려준다. (lastLoginAt 이 최신이어야 한다)
        return AdminMeResponse.from(loadAdmin(principal.getAdminId()));
    }

    /**
     * 로그아웃. 세션을 통째로 무효화한다.
     *
     * <p>세션 방식의 장점이 여기서 드러난다. 서버가 상태를 들고 있으므로
     * 무효화하는 즉시 그 쿠키는 쓸 수 없게 된다. (JWT는 만료 전까지 유효하다)
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            log.info("[로그아웃] sessionId={}", session.getId());
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        // 세션을 무효화해도 브라우저에는 쿠키가 남는다. 이후 요청마다 죽은 세션 ID가 실려 오므로
        // 만료된 쿠키를 덮어써서 지운다. (지우지 않아도 동작은 하지만 디버깅할 때 헷갈린다)
        ResponseCookie expired = ResponseCookie.from(sessionCookieName, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    }

    @Transactional(readOnly = true)
    public AdminMeResponse findMe(Long adminId) {
        return AdminMeResponse.from(loadAdmin(adminId));
    }

    /**
     * 비밀번호 변경.
     *
     * <p>변경 후에도 세션은 유지한다. 끊고 싶다면 컨트롤러에서 {@link #logout} 을 이어서 호출하면 된다.
     */
    @Transactional
    public void changePassword(Long adminId, String currentPassword, String newPassword) {
        AdminUserJpaEntity admin = loadAdmin(adminId);

        if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.PASSWORD_NOT_MATCHED);
        }

        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.PASSWORD_SAME_AS_OLD);
        }

        admin.changePassword(passwordEncoder.encode(newPassword));
        log.info("[비밀번호 변경] adminId={}", adminId);
    }

    // ------------------------------------------------------------------

    /**
     * 아이디/비밀번호 검증.
     *
     * <p>{@code DaoAuthenticationProvider} 는 비밀번호를 확인하기 <b>전에</b> 잠금 여부를 먼저 본다.
     * 덕분에 잠긴 계정은 비밀번호가 맞아도 LockedException 으로 걸러진다.
     */
    private Authentication authenticate(String username, String rawPassword) {
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, rawPassword));

        } catch (LockedException e) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);

        } catch (DisabledException e) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);

        } catch (BadCredentialsException e) {
            // 계정이 없어도 여기로 온다. (UsernameNotFoundException 은 기본 설정에서 BadCredentials 로 감춰진다)
            loginAttemptRecorder.recordFailure(username);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);

        } catch (AuthenticationException e) {
            log.warn("[로그인 실패] 분류되지 않은 인증 예외: {}", e.toString());
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }

    private AdminUserJpaEntity loadAdmin(Long adminId) {
        return adminUserJpaRepository.findById(adminId)
                // 로그인 중에 계정이 삭제된 경우. 세션은 살아 있지만 더는 유효한 관리자가 아니다.
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }
}
