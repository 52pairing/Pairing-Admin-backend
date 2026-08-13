package com.pairing.admin.matching.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "AI matching diagnostics")
public record MatchingDiagnosticsResponse(
        ProjectInfo project,
        PositionInfo position,
        SnapshotInfo snapshots,
        EmbeddingInfo embeddings,
        RoundInfo round,
        CountInfo counts,
        LastAiLogInfo lastAiLog
) {

    public record ProjectInfo(Long projectId, String title, String status, String paymentStatus) {
    }

    public record PositionInfo(Long positionId, String status, String jobCategory, String jobRole) {
    }

    public record SnapshotInfo(boolean projectSnapshotExists, boolean positionSnapshotExists) {
    }

    /**
     * @param positionDimension 저장된 포지션 벡터의 실제 차원. 임베딩 모델을 바꿨을 때 옛 차원 벡터와
     *                          새 차원 벡터가 섞이는 것을 잡기 위한 값이다. 벡터가 없으면 null
     */
    public record EmbeddingInfo(boolean positionEmbeddingExists, String positionModel,
                                Integer positionDimension, long freelancerEmbeddingCount) {
    }

    public record RoundInfo(Long roundId, Integer roundNo, String roundType, String status) {
    }

    public record CountInfo(long candidateCount, long exposedCandidateCount, long requestCount) {
    }

    public record LastAiLogInfo(String status, LocalDateTime createdAt, String errorMessage) {
    }
}
