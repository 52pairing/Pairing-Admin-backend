package com.pairing.admin.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 도메인 에러코드 Enum이 구현하는 공통 계약.
 * 이 인터페이스를 구현해두면 {@link GlobalExceptionHandler}가 자동으로 처리한다.
 */
public interface BaseErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}
