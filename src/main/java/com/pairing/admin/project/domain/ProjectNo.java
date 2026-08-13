package com.pairing.admin.project.domain;

/**
 * 화면 표시용 프로젝트 번호({@code PRJ-001}).
 *
 * <p><b>저장된 값이 아니다.</b> {@code project} 테이블에는 번호 컬럼이 없어서 ID 로 만든 표기다.
 * {@code contract.contract_no} 나 {@code settlement.settlement_no} 처럼 채번된 식별자가 아니므로
 * 외부 노출이나 조회 키로 쓰면 안 된다. 조회는 언제나 {@code projectId} 로 한다.
 * (필요해지면 백엔드에 컬럼을 만드는 게 맞다 — {@code NegotiationNo} 와 같은 사정이다)
 *
 * <p>목록과 상세가 <b>같은 번호를 보여야</b> 해서 규칙을 이 한 곳에 둔다.
 */
public final class ProjectNo {

    private ProjectNo() {
    }

    /**
     * {@code PRJ-{ID 3자리}}. ID 가 1000 을 넘으면 자릿수가 늘어난다(자르지 않는다).
     *
     * <p>피그마의 {@code PRJ-001} 이 이 규칙이다. 앞을 잘라 붙이면 서로 다른 프로젝트가
     * 같은 번호로 보이므로 넘치는 자리는 그대로 둔다.
     */
    public static String of(Long projectId) {
        return projectId == null ? null : "PRJ-%03d".formatted(projectId);
    }
}
