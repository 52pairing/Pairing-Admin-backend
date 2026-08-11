package com.pairing.admin.global.common.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(

        @Schema(description = "에러 발생 시각", example = "2026-08-11T07:09:00Z")
        Instant timestamp,

        @Schema(description = "HTTP 상태 코드", example = "401")
        int status,

        @Schema(description = "에러 분류 코드", example = "ADMIN_AUTH_001")
        String errorCode,

        @Schema(description = "에러 상세 메시지", example = "아이디 또는 비밀번호가 올바르지 않습니다.")
        String message,

        @Schema(description = "에러 추적 ID (로그 확인용)", example = "a1b2c3d4")
        String traceId
) {}
