package com.pairing.admin.settlement.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementAdminErrorCode implements BaseErrorCode {

    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_SETTLEMENT_001",
            "정산 내역을 찾을 수 없습니다."),

    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "ADMIN_SETTLEMENT_002",
            "조회 시작일이 종료일보다 늦습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
