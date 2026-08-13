package com.pairing.admin.settlement.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 수수료 단계. 화면의 "유형" 컬럼이자 착수금/완료금 필터다.
 *
 * <p><b>백엔드 {@code com.pairing.settlement.domain.model.SettlementPhase} 의 사본이다.</b>
 * 요구사항 R39 는 "완료금 수수료" 라고 부르지만 DB 값은 {@code SUCCESS_FEE}(성공보수 수수료)다.
 * 백엔드 라벨을 따른다 — 정산 상세와 관리자 화면이 같은 건을 다르게 부르면 대조할 수 없다.
 *
 * <p>플랫폼을 거치는 돈은 이 수수료뿐이다. 실제 용역비는 클라이언트가 프리랜서에게
 * 직접 지급하므로 정산 목록에 나타나지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementPhase {

    DEPOSIT("착수금 수수료"),
    SUCCESS_FEE("성공보수 수수료");

    private final String label;

    public static SettlementPhase find(String code) {
        if (code == null) {
            return null;
        }
        for (SettlementPhase phase : values()) {
            if (phase.name().equals(code)) {
                return phase;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        SettlementPhase phase = find(code);
        return phase == null ? code : phase.label;
    }
}
