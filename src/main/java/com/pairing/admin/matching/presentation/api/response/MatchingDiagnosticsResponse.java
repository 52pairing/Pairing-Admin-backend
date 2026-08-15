package com.pairing.admin.matching.presentation.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 매칭 진단 결과(포지션 1건).
 *
 * <p><b>상태 코드에는 화면 문구를 함께 내려준다</b>({@code status} + {@code statusLabel}).
 * 코드는 프론트의 색상·아이콘 분기용이고, 문구는 그대로 찍는 값이다. 문구를 파생 메서드로 두는 이유는
 * 코드와 문구가 <b>따로 들어올 수 없게</b> 만들기 위해서다 - 생성자 인자로 받으면 호출부에서 짝이
 * 어긋난 값을 넣을 수 있다. 표는 {@link MatchingCodeLabel} 한곳에 있다.
 */
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

        @JsonProperty("statusLabel")
        public String statusLabel() {
            return MatchingCodeLabel.projectStatus(status);
        }

        @JsonProperty("paymentStatusLabel")
        public String paymentStatusLabel() {
            return MatchingCodeLabel.paymentStatus(paymentStatus);
        }
    }

    public record PositionInfo(Long positionId, String status, String jobCategory, String jobRole) {

        /** 프로젝트 상태와 코드가 겹치지만 문구가 다르다("종료" vs "모집 종료"). */
        @JsonProperty("statusLabel")
        public String statusLabel() {
            return MatchingCodeLabel.positionStatus(status);
        }
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

        @JsonProperty("roundTypeLabel")
        public String roundTypeLabel() {
            return MatchingCodeLabel.roundType(roundType);
        }

        @JsonProperty("statusLabel")
        public String statusLabel() {
            return MatchingCodeLabel.roundStatus(status);
        }
    }

    public record CountInfo(long candidateCount, long exposedCandidateCount, long requestCount) {
    }

    public record LastAiLogInfo(String status, LocalDateTime createdAt, String errorMessage) {

        @JsonProperty("statusLabel")
        public String statusLabel() {
            return MatchingCodeLabel.aiLogStatus(status);
        }
    }
}
