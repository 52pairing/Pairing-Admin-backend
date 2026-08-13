package com.pairing.admin.negotiation.domain;

import java.time.LocalDateTime;

/**
 * 화면 표시용 협상번호.
 *
 * <p><b>저장된 값이 아니다.</b> 협상 테이블에 그런 컬럼이 없어서 ID 와 시작 연도로 만든 표기이고,
 * 계약번호({@code contract_no}) 처럼 채번된 식별자가 아니다. 외부에 노출하거나 조회 키로 쓰면
 * 안 된다 — 필요해지면 백엔드에 컬럼을 만드는 게 맞다.
 *
 * <p>목록과 상세가 <b>같은 번호를 보여야</b> 해서 규칙을 여기 한 곳에 둔다. 화면마다 따로 만들면
 * 같은 협상이 목록에서는 {@code NEG-2026-010}, 상세에서는 {@code NEG-10} 으로 보이는 일이 생긴다.
 */
public final class NegotiationNo {

    private NegotiationNo() {
    }

    /**
     * {@code NEG-{시작연도}-{ID 3자리}}.
     *
     * <p>시작 시각이 없으면 연도를 0 으로 둔다. 시작 시각은 NOT NULL 이라 정상 데이터에서는
     * 일어나지 않고, 일어났다면 {@code NEG-0-007} 처럼 눈에 띄는 편이 조용히 올해로 채우는
     * 것보다 낫다.
     */
    public static String of(Long negotiationId, LocalDateTime startedAt) {
        int year = startedAt == null ? 0 : startedAt.getYear();
        return "NEG-%d-%03d".formatted(year, negotiationId);
    }
}
