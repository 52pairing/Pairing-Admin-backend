package com.pairing.admin.global.exception;

import com.pairing.admin.global.common.api.response.ErrorResponse;
import com.pairing.admin.global.filter.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리. 모든 에러 응답을 {@link ErrorResponse} 하나로 통일한다.
 *
 * <p>주의: 시큐리티 <b>필터</b>에서 터진 인증 예외는 여기까지 오지 않는다.
 * 그쪽은 {@code CustomAuthenticationEntryPoint} / {@code CustomAccessDeniedHandler} 가 처리한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 의도적으로 던진 비즈니스 예외. 스택트레이스 없이 한 줄만 남긴다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        BaseErrorCode errorCode = e.getErrorCode();
        log.warn("[BusinessException] code={}, message={}", errorCode.getCode(), e.getMessage());
        return toResponse(errorCode, e.getMessage());
    }

    /** @Valid 검증 실패. 어떤 필드가 왜 틀렸는지 메시지에 담아 준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("[ValidationException] {}", detail);
        return toResponse(GlobalErrorCode.INVALID_REQUEST,
                detail.isBlank() ? GlobalErrorCode.INVALID_REQUEST.getMessage() : detail);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        log.warn("[BadRequest] {}", e.getMessage());
        return toResponse(GlobalErrorCode.INVALID_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(GlobalErrorCode.METHOD_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return toResponse(GlobalErrorCode.API_NOT_FOUND, e.getMessage());
    }

    /** 컨트롤러/서비스의 @PreAuthorize 에서 막힌 경우. (필터 단계는 AccessDeniedHandler 가 처리) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("[AccessDenied] {}", e.getMessage());
        return toResponse(GlobalErrorCode.ACCESS_DENIED, GlobalErrorCode.ACCESS_DENIED.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        log.warn("[AuthenticationException] {}", e.getMessage());
        return toResponse(GlobalErrorCode.UNAUTHORIZED, GlobalErrorCode.UNAUTHORIZED.getMessage());
    }

    /**
     * 마지막 안전망. 예상하지 못한 예외이므로 스택트레이스를 통째로 남긴다.
     * 응답에는 내부 메시지를 노출하지 않고 traceId만 준다. (로그와 대조해서 추적)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("[UnhandledException] {}", e.toString(), e);
        return toResponse(GlobalErrorCode.SERVER_ERROR, GlobalErrorCode.SERVER_ERROR.getMessage());
    }

    private ResponseEntity<ErrorResponse> toResponse(BaseErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(
                        Instant.now(),
                        errorCode.getStatus().value(),
                        errorCode.getCode(),
                        message,
                        TraceIdFilter.currentTraceId()
                ));
    }
}
