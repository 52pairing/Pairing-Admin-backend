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
     * @param positionEmbeddingDimension 저장된 포지션 벡터의 실제 차원. 임베딩 모델을 바꿨을 때
     *                                   <b>옛 차원 벡터와 새 차원 벡터가 섞이는 것</b>을 잡기 위한 값이다.
     *                                   섞이면 유사도 비교 자체가 무의미해진다. 벡터가 없으면 null
     * @param issues 비어 있으면 정상이다. 코드와 화면 문구를 함께 준다 - 문구를 프론트가 들고 있으면
     *               진단 항목을 추가할 때 양쪽 배포를 맞춰야 하고, 어긋나면 관리자 화면에 영문 코드가
     *               그대로 노출된다. 색상·아이콘 분기는 {@code code}로 하면 된다
     */
    public record PositionDiagnostics(
            MatchingDiagnosticsResponse.PositionInfo position,
            boolean positionSnapshotExists,
            boolean positionEmbeddingExists,
            String positionModel,
            Integer positionEmbeddingDimension,
            long freelancerEmbeddingCount,
            MatchingDiagnosticsResponse.RoundInfo round,
            MatchingDiagnosticsResponse.CountInfo counts,
            MatchingDiagnosticsResponse.LastAiLogInfo lastAiLog,
            List<Issue> issues
    ) {
    }

    /** 진단 항목 1건. {@code code}는 분기용, {@code message}는 화면 표기용이다. */
    public record Issue(String code, String message) {
    }

    /**
     * 포지션 진단 항목. <b>코드와 문구를 한곳에 묶어둔다</b> - 나뉘어 있으면 코드를 추가할 때 문구를
     * 빠뜨린다.
     *
     * <p>여기에 항목을 추가하면 목록의 issueCount 를 계산하는 SQL 식
     * ({@code MatchingAdminRepository.POSITION_ISSUE_EXPRESSION})도 함께 고쳐야 한다. 두 곳이 갈리면
     * "목록은 2건인데 상세는 3건"이 되고, {@code MatchingAdminDiagnosticsTest}가 그걸 잡는다.
     */
    public enum IssueType {
        POSITION_SNAPSHOT_MISSING("모집 시작 시점 스냅샷이 없습니다. 매칭 요청 카드가 프로젝트 수정 내용으로 바뀝니다."),
        POSITION_EMBEDDING_MISSING("포지션 벡터가 없습니다. 후보 검색이 불가능합니다."),
        ROUND_MISSING("추천 라운드가 만들어지지 않았습니다. 모집 시작 이벤트가 처리되지 않았습니다."),
        NO_EXPOSED_CANDIDATE("추천 라운드는 있으나 노출된 후보가 없습니다. 하드필터 또는 LLM 응답을 확인해야 합니다.");

        private final String message;

        IssueType(String message) {
            this.message = message;
        }

        public Issue toIssue() {
            return new Issue(name(), message);
        }
    }
}
