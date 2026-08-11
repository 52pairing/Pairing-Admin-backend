package com.pairing.admin.global.common.api.response;

import java.time.Instant;

/**
 * 성공 응답의 공통 표현 형식. 백엔드(Pairing-backend)와 같은 형태를 유지한다.
 *
 * <p>관리자 프론트가 백엔드 API와 관리자 API를 함께 호출할 때 응답 파싱 코드를 나눌 필요가 없도록
 * 필드 구성을 일부러 동일하게 맞췄다.
 */
public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(Instant.now(), 200, code, message, data);
    }

    public static <T> ApiResponse<T> created(String code, String message, T data) {
        return new ApiResponse<>(Instant.now(), 201, code, message, data);
    }

    public static ApiResponse<Void> success(String code, String message) {
        return new ApiResponse<>(Instant.now(), 200, code, message, null);
    }
}
