package com.pairing.admin.negotiation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 협상 세션 상태. <b>백엔드의 같은 이름 enum 과 값이 일치해야 한다</b>(negotiation.status 를 그대로 읽는다).
 *
 * <p>백엔드에는 15라운드 소진 시 자동 결렬(FAILED) 정책이 있어 상태는 이 셋으로 닫힌다.
 * 값이 늘어나면 여기도 같이 늘려야 조회에서 IllegalArgumentException 이 난다.
 */
@Getter
@RequiredArgsConstructor
public enum NegotiationStatus {

    IN_PROGRESS("진행중"),
    AGREED("타결"),
    FAILED("결렬");

    private final String label;
}
