package com.pairing.admin.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계정 역할. 백엔드(Pairing-backend)의 {@code com.pairing.account.domain.model.Role} 과
 * <b>이름이 정확히 같아야 한다.</b> DB에 문자열로 저장되기 때문이다.
 */
@Getter
@RequiredArgsConstructor
public enum Role {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서"),
    ADMIN("관리자");

    private final String label;
}
