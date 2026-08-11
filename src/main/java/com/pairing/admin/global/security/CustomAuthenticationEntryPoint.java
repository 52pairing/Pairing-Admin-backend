package com.pairing.admin.global.security;

import com.pairing.admin.global.exception.ErrorResponseWriter;
import com.pairing.admin.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증되지 않은 요청(401).
 *
 * <p>세션 방식의 기본 동작은 로그인 페이지로 302 리다이렉트다. 관리자 프론트는 SPA라
 * 리다이렉트를 받으면 로그인 HTML을 JSON으로 파싱하려다 실패한다. 401 JSON으로 바꿔
 * 프론트가 "세션 만료 → 로그인 화면 이동"을 스스로 판단하게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("[401] 인증되지 않은 요청: {} {}", request.getMethod(), request.getRequestURI());
        errorResponseWriter.write(response, GlobalErrorCode.UNAUTHORIZED);
    }
}
