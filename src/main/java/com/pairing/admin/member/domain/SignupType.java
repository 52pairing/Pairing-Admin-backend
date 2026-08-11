package com.pairing.admin.member.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 가입 경로. 백엔드와 이름이 정확히 같아야 한다. (DB에 문자열로 저장) */
@Getter
@RequiredArgsConstructor
public enum SignupType {

    EMAIL("이메일"),
    SOCIAL("소셜");

    private final String label;
}
