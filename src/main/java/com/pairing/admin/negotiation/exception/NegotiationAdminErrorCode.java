package com.pairing.admin.negotiation.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NegotiationAdminErrorCode implements BaseErrorCode {

    NEGOTIATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_NEGOTIATION_001",
            "협상을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
