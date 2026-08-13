package com.pairing.admin.negotiation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * {@code ai_agent_log.agent_type} — 어느 대리인이 낸 호출인가.
 *
 * <p>이 로그 테이블은 <b>협상 전용이 아니다.</b> 이력서 파싱·매칭·챗봇·계약서까지 전부 같은 표에
 * 쌓인다. 그래서 토큰 사용량을 볼 때는 반드시 대상을 좁혀야 하고, 좁힌 뒤에도 어떤 대리인이
 * 얼마를 썼는지 구분해서 보여 준다.
 *
 * <p>파이썬의 {@code AgentType}(app/domains/ai_log/repository.py) 과 값이 일치해야 한다.
 * 파이썬이 새 값을 넣으면 여기서 {@code valueOf} 가 터지므로, 변환은
 * {@link #from(String)} 으로만 한다.
 */
@Getter
@RequiredArgsConstructor
public enum AgentType {

    PARSER("이력서 파싱"),
    EMBEDDING("임베딩"),
    MATCHER("매칭"),
    GUARD("직무·스킬 재검증"),
    NEGOTIATOR("협상"),
    CONTRACT("계약서"),
    CHATBOT("챗봇"),

    /**
     * 아는 값이 아닐 때.
     *
     * <p>파이썬에 대리인이 하나 늘었다고 관리자 화면이 500 으로 죽으면 안 된다. 토큰 사용량은
     * 운영을 <b>지켜보기 위한</b> 화면이라, 모르는 값이 와도 일단 보여 주는 쪽이 맞다.
     */
    UNKNOWN("기타");

    private final String label;

    /** DB 값 → enum. 아는 값이 아니면 {@link #UNKNOWN}. */
    public static AgentType from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (AgentType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
