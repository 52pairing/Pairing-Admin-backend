package com.pairing.admin.negotiation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 협상 쟁점 종류. 백엔드의 같은 이름 enum 과 값이 일치해야 한다.
 *
 * <p>{@code SCOPE}(작업 범위)는 값이 들어온 적이 없다. 협상은 프로젝트에 이미 적힌 작업 범위를
 * 두고 <b>금액·기간·근무 조건만</b> 조율하기 때문이다. 와이어 초안에 "작업 범위" 칸이 있었지만
 * 항상 비어서 최종안에서 빠졌다. enum 값 자체는 백엔드와 맞추려고 남겨 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum ConditionType {

    AMOUNT("단가(월)"),
    PERIOD("기간"),
    START_DATE("시작일"),
    WORK_STYLE("근무 방식"),
    WORK_FORM("근무 형태"),
    SCOPE("작업 범위"),
    OTHER("기타");

    private final String label;
}
