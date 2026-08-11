package com.pairing.admin.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 traceId를 발급해 MDC와 응답 헤더(X-Trace-Id)에 심는다.
 *
 * <p>순서가 중요하다. 스프링 시큐리티 필터체인은 order = -100 으로 등록되므로,
 * 순서를 지정하지 않으면 이 필터가 시큐리티 체인보다 <b>뒤</b>에 실행된다.
 * 그러면 401/403 핸들러가 응답을 만드는 시점에 MDC가 비어 있어 traceId 없는 에러 응답이 나간다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);
        request.setAttribute(TRACE_ID_KEY, traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 스레드 풀 재사용 시 이전 요청의 traceId가 남지 않도록 반드시 비운다.
            MDC.clear();
        }
    }

    /** 현재 요청의 traceId. MDC → request attribute 순으로 찾고, 둘 다 없으면 새로 만든다. */
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null) {
            return traceId;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String cached = (String) attributes.getRequest().getAttribute(TRACE_ID_KEY);
            if (cached != null) {
                return cached;
            }
        }

        return UUID.randomUUID().toString().substring(0, 8);
    }
}
