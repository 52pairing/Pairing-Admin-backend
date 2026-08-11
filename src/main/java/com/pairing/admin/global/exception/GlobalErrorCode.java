package com.pairing.admin.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {

    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "ADMIN_GLOBAL_001", "서버 내부에서 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "ADMIN_GLOBAL_002", "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "ADMIN_GLOBAL_003", "지원하지 않는 HTTP 메서드입니다."),
    API_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_GLOBAL_004", "요청하신 API 경로를 찾을 수 없습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ADMIN_GLOBAL_005", "접근 권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ADMIN_GLOBAL_006", "로그인이 필요합니다. 세션이 만료되었을 수 있습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ADMIN_GLOBAL_007", "파일 업로드에 실패했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "ADMIN_GLOBAL_008", "허용되지 않는 파일 형식입니다."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "ADMIN_GLOBAL_009", "CSRF 토큰이 유효하지 않습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
