package com.pairing.admin.negotiation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 발언 종류. 백엔드의 같은 이름 enum 과 값이 일치해야 한다. */
@Getter
@RequiredArgsConstructor
public enum NegotiationMessageType {

    PROPOSAL("제안"),
    RESPONSE("응답"),
    SYSTEM("안내");

    private final String label;
}
