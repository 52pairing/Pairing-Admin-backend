package com.pairing.admin.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CSRF 토큰을 실제로 꺼내서 XSRF-TOKEN 쿠키가 응답에 실리게 만든다.
 *
 * <p>스프링 시큐리티 6부터 CSRF 토큰은 "지연 로딩"이라, 아무도 값을 읽지 않으면
 * 쿠키가 발급되지 않는다. 서버 렌더링 화면이라면 템플릿이 토큰을 읽어서 자연히 발급되지만,
 * SPA는 읽는 주체가 없어서 첫 GET 응답에 쿠키가 안 실리고 이어지는 POST가 403으로 막힌다.
 *
 * <p>{@code getToken()} 한 번 호출하는 것이 전부다. 그 부수효과로 쿠키가 나간다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }

        filterChain.doFilter(request, response);
    }
}
