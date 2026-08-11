package com.pairing.admin.global.security;

import com.pairing.admin.global.exception.ErrorResponseWriter;
import com.pairing.admin.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증은 됐지만 권한이 없는 요청(403). CSRF 토큰 오류도 여기로 들어온다.
 *
 * <p>CSRF 실패와 권한 부족은 프론트의 대응이 완전히 다르다.
 * (전자는 토큰 재발급 후 재시도, 후자는 안내 문구) 그래서 에러 코드를 나눠 준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        // CsrfException(토큰 없음/불일치)은 AccessDeniedException 을 상속하므로 같은 핸들러로 들어온다.
        if (accessDeniedException instanceof CsrfException) {
            log.warn("[403] CSRF 토큰 오류: {} {}", request.getMethod(), request.getRequestURI());
            errorResponseWriter.write(response, GlobalErrorCode.CSRF_TOKEN_INVALID);
            return;
        }

        log.warn("[403] 권한 없는 요청: {} {}", request.getMethod(), request.getRequestURI());
        errorResponseWriter.write(response, GlobalErrorCode.ACCESS_DENIED);
    }
}
