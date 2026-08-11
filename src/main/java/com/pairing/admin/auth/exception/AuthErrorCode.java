package com.pairing.admin.auth.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    // 계정이 없는 경우와 비밀번호가 틀린 경우를 같은 코드로 묶는다.
    // 나누면 "이 아이디는 존재한다" 는 정보를 공격자에게 알려주게 된다.
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_001",
            "아이디 또는 비밀번호가 올바르지 않습니다."),

    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "ADMIN_AUTH_002",
            "계정이 잠겨 있습니다. 다른 관리자에게 잠금 해제를 요청해 주세요."),

    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "ADMIN_AUTH_003",
            "사용할 수 없는 계정입니다."),

    PASSWORD_NOT_MATCHED(HttpStatus.BAD_REQUEST, "ADMIN_AUTH_004",
            "현재 비밀번호가 올바르지 않습니다."),

    PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "ADMIN_AUTH_005",
            "새 비밀번호가 기존 비밀번호와 같습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
