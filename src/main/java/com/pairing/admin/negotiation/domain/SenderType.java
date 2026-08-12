package com.pairing.admin.negotiation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 발신 주체. 대리인(AI)과 사람을 구분해 보여 준다. 백엔드의 같은 이름 enum 과 값이 일치해야 한다. */
@Getter
@RequiredArgsConstructor
public enum SenderType {

    CLIENT_AGENT("클라이언트 AI"),
    FREELANCER_AGENT("프리랜서 AI"),
    CLIENT("클라이언트"),
    FREELANCER("프리랜서"),
    SYSTEM("시스템");

    private final String label;

    /** 대리인이 낸 발언인가. 관리자 화면에서 사람 개입 지점을 구분하는 데 쓴다. */
    public boolean isAgent() {
        return this == CLIENT_AGENT || this == FREELANCER_AGENT;
    }
}
