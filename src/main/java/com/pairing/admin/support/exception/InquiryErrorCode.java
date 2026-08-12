package com.pairing.admin.support.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InquiryErrorCode implements BaseErrorCode {

    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_INQUIRY_001",
            "문의를 찾을 수 없습니다."),

    INVALID_ANSWER(HttpStatus.BAD_REQUEST, "ADMIN_INQUIRY_002",
            "답변 내용을 입력해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
