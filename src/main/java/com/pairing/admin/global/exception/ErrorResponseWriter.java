package com.pairing.admin.global.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.admin.global.common.api.response.ErrorResponse;
import com.pairing.admin.global.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 시큐리티 필터 단계에서 발생한 예외를 컨트롤러와 같은 JSON 형태로 내보낸다.
 *
 * <p>필터에서 터진 예외는 {@code @RestControllerAdvice} 까지 도달하지 않는다.
 * 이 클래스가 없으면 로그인 실패·세션 만료 응답만 스프링 기본 HTML/JSON 포맷이 되어
 * 프론트가 두 가지 에러 형태를 파싱해야 한다.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        write(response, errorCode, errorCode.getMessage());
    }

    public void write(HttpServletResponse response, BaseErrorCode errorCode, String message) throws IOException {
        // 이미 응답이 나간 뒤라면 덮어쓸 수 없다. (예: 비동기 처리 중 커밋)
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                message,
                TraceIdFilter.currentTraceId()
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
