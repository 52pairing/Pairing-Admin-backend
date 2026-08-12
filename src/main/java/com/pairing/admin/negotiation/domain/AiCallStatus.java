package com.pairing.admin.negotiation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** {@code ai_agent_log.status} — 모델 호출 1건의 결과. */
@Getter
@RequiredArgsConstructor
public enum AiCallStatus {

    SUCCESS("성공"),
    FAILED("실패"),

    /** 아는 값이 아닐 때. 이유는 {@link AgentType#UNKNOWN} 과 같다. */
    UNKNOWN("기타");

    private final String label;

    public static AiCallStatus from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (AiCallStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
