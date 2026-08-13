package com.pairing.admin.matching.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 매칭 진단 대상 프로젝트 1건(목록용).
 *
 * <p>진단 화면이 projectId/positionId를 직접 입력받으면 관리자가 무슨 번호를 넣어야 할지 알 수 없다.
 * 이 목록에서 고르고 상세로 들어가는 흐름을 만들기 위한 응답이다.
 *
 * <p><b>착수금 결제가 끝난 프로젝트만 나온다.</b> 임베딩·스냅샷·추천 라운드는 모집 시작(착수금 결제
 * 완료) 시점에 만들어지므로, 결제 전 프로젝트는 그것들이 없는 게 정상이다. 목록에 섞으면 정상인 건이
 * 전부 문제처럼 보인다.
 *
 * @param issueCount 문제로 보이는 포지션 수. 0이면 그 프로젝트는 확인할 게 없다는 뜻이라 목록에서 바로
 *                   걸러낼 수 있다. 판정 기준은
 *                   {@link MatchingProjectDiagnosticsResponse.PositionDiagnostics#issues()} 와 같다
 * @param clientName 발주 클라이언트 회사명. 프로젝트명만으로는 어느 회사 건인지 알기 어려워서 함께 준다
 * @param recruitStartedAt 모집 시작(착수금 결제 완료) 시각. <b>문제의 심각도를 가늠하는 값이다</b> -
 *                         "사흘 전에 모집을 시작했는데 아직 후보가 0명"인지, "방금 시작해서 아직
 *                         처리 중"인지가 이 값 없이는 구분되지 않는다
 * @param lastAiLogAt 이 프로젝트의 포지션들에 대한 마지막 AI 호출 시각. 한 번도 없으면 null이며,
 *                    그 자체가 "매칭이 아예 돌지 않았다"는 신호다
 */
@Schema(description = "AI matching diagnostics target project")
public record MatchingProjectSummaryResponse(
        Long projectId,
        String title,
        String clientName,
        String status,
        String paymentStatus,
        LocalDateTime recruitStartedAt,
        int positionCount,
        int issueCount,
        LocalDateTime lastAiLogAt
) {
}
