package com.pairing.admin.matching.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 프로젝트 1건의 모든 포지션 진단 결과(상세용).
 *
 * <p>기존 {@code GET /diagnostics}는 포지션 하나만 본다. 그건 포지션을 깊게 볼 때 쓰고, 이 응답은
 * "이 프로젝트의 어느 포지션이 문제인가"를 한 화면에서 훑을 때 쓴다.
 *
 * <p>포지션 단위 항목은 {@link MatchingDiagnosticsResponse}의 중첩 레코드를 그대로 재사용한다.
 * 같은 값을 다른 모양으로 두 번 정의하면 한쪽만 고쳐져서 단건 조회와 목록 조회의 숫자가 갈린다.
 */
@Schema(description = "AI matching diagnostics for every position of a project")
public record MatchingProjectDiagnosticsResponse(
        MatchingDiagnosticsResponse.ProjectInfo project,
        boolean projectSnapshotExists,
        int positionCount,
        int issueCount,
        List<PositionDiagnostics> positions
) {

    /**
     * @param freelancerEmbeddingCount 이 포지션의 직군·직무·요구스킬로 좁힌 <b>후보 풀 크기</b>다.
     *                                 전체 프리랜서 수가 아니라 포지션마다 값이 다르다
     * @param issues 비어 있으면 정상이다. 값은 화면에서 그대로 쓰기 위한 코드이며 아래 넷뿐이다.
     *               <ul>
     *                 <li>{@code POSITION_SNAPSHOT_MISSING} - 모집 시작 시점 스냅샷이 안 얼려졌다.
     *                 매칭 요청 카드가 프로젝트 수정 내용으로 바뀐다(R32 위반)</li>
     *                 <li>{@code POSITION_EMBEDDING_MISSING} - 포지션 벡터가 없다. 후보 검색 자체가
     *                 불가능하다</li>
     *                 <li>{@code ROUND_MISSING} - 추천 라운드가 한 번도 안 만들어졌다. 모집 시작
     *                 이벤트가 처리되지 않았다는 뜻이다</li>
     *                 <li>{@code NO_EXPOSED_CANDIDATE} - 라운드는 있는데 노출 후보가 0명이다.
     *                 하드필터에서 전멸했거나 LLM 응답이 비었다</li>
     *               </ul>
     */
    public record PositionDiagnostics(
            MatchingDiagnosticsResponse.PositionInfo position,
            boolean positionSnapshotExists,
            boolean positionEmbeddingExists,
            String positionModel,
            long freelancerEmbeddingCount,
            MatchingDiagnosticsResponse.RoundInfo round,
            MatchingDiagnosticsResponse.CountInfo counts,
            MatchingDiagnosticsResponse.LastAiLogInfo lastAiLog,
            List<String> issues
    ) {
    }
}
