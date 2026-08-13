package com.pairing.admin.project.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProjectAdminErrorCode implements BaseErrorCode {

    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_PROJECT_001",
            "프로젝트를 찾을 수 없습니다."),

    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "ADMIN_PROJECT_002",
            "조회 시작일이 종료일보다 늦습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
