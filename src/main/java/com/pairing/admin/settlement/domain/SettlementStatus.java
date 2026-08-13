package com.pairing.admin.settlement.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정산 상태. 화면의 상태 필터가 이 값 그대로다.
 *
 * <p><b>백엔드 {@code com.pairing.settlement.domain.model.SettlementStatus} 의 사본이다.</b>
 *
 * <p>요구사항 R39 의 필터 문구는 "결제 완료 · 가능 · 미납 · 실패" 인데, DB 에 "결제 가능" 이라는
 * 상태는 없다. 아직 내지 않았고 기한도 지나지 않은 {@link #PENDING} 이 그것이다. 라벨을
 * "결제 대기" 로 두는 이유는 백엔드와 같은 말을 써야 두 화면을 대조할 수 있기 때문이다.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementStatus {

    PENDING("결제 대기"),
    PAID("결제 완료"),
    OVERDUE("미납"),
    FAILED("결제 실패"),

    /** 프로젝트가 등록 취소돼 낼 이유가 사라진 정산. 결제 대상에서 빠지고 이력으로만 남는다. */
    CANCELED("취소됨");

    private final String label;

    /**
     * 모르는 코드가 와도 예외를 던지지 않는다.
     *
     * <p>{@code valueOf} 는 백엔드에 상태가 하나 추가된 순간 정산 목록 전체를 터뜨린다.
     * 금액을 보는 화면이 상태값 하나 때문에 통째로 죽으면 안 된다.
     */
    public static SettlementStatus find(String code) {
        if (code == null) {
            return null;
        }
        for (SettlementStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        SettlementStatus status = find(code);
        return status == null ? code : status.label;
    }
}
