package com.pairing.admin.member.exception;

import com.pairing.admin.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN_MEMBER_001",
            "회원을 찾을 수 없습니다."),

    // 관리자 계정은 admin_user 테이블로 분리되어 있으므로 원래는 여기에 걸릴 일이 없다.
    // 다만 account 테이블에 role='ADMIN' 인 과거 데이터가 남아 있을 수 있어 방어적으로 막는다.
    CANNOT_MODIFY_ADMIN(HttpStatus.FORBIDDEN, "ADMIN_MEMBER_002",
            "관리자 역할의 계정은 이 화면에서 변경할 수 없습니다."),

    ALREADY_IN_STATUS(HttpStatus.CONFLICT, "ADMIN_MEMBER_003",
            "이미 해당 상태입니다."),

    WITHDRAWN_MEMBER(HttpStatus.CONFLICT, "ADMIN_MEMBER_004",
            "탈퇴한 회원은 상태를 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
