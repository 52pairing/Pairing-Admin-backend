package com.pairing.admin.negotiation.presentation.api.response;

import com.pairing.admin.negotiation.domain.NegotiationNo;
import com.pairing.admin.negotiation.domain.NegotiationStatus;
import com.pairing.admin.negotiation.infrastructure.persistence.NegotiationAdminRepository;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * AI Agent 관리 목록 한 줄.
 *
 * <p>상태는 코드와 라벨을 <b>둘 다</b> 내려준다. 화면은 라벨을 그대로 찍고, 필터·정렬처럼
 * 값 비교가 필요한 곳은 코드를 쓴다. 프론트에 코드→한글 표를 다시 두면 백엔드에 상태가
 * 하나 늘 때마다 양쪽을 고쳐야 하고, 빠뜨리면 화면에 영문 코드가 그대로 노출된다.
 */
@Schema(description = "협상 목록 항목")
public record NegotiationListItemResponse(

        @Schema(description = "협상 ID. <b>상세 조회 키는 이 값이다</b>", example = "10")
        Long negotiationId,

        @Schema(description = "화면 표시용 협상번호. 저장된 값이 아니라 ID·시작연도로 만든 표기라 "
                + "조회 키로 쓰면 안 된다. 상세와 같은 규칙으로 만든다", example = "NEG-2026-010")
        String negotiationNo,

        @Schema(description = "프로젝트 ID", example = "18")
        Long projectId,

        @Schema(description = "프로젝트명", example = "로봇개 프로그램 개발")
        String projectTitle,

        @Schema(description = "클라이언트명", example = "유어커피")
        String clientName,

        @Schema(description = "프리랜서명", example = "김개발3")
        String freelancerName,

        @Schema(description = "상태 코드", example = "AGREED")
        NegotiationStatus status,

        @Schema(description = "상태 라벨", example = "타결")
        String statusLabel,

        @Schema(description = "진행 라운드 수", example = "5")
        int totalRound,

        @Schema(description = "시작 시각")
        LocalDateTime startedAt,

        @Schema(description = "종료 시각. 진행 중이면 null")
        LocalDateTime endedAt
) {

    public static NegotiationListItemResponse from(NegotiationAdminRepository.ListRow row) {
        NegotiationStatus status = NegotiationStatus.valueOf(row.getStatus());
        return new NegotiationListItemResponse(
                row.getNegotiationId(),
                NegotiationNo.of(row.getNegotiationId(), row.getStartedAt()),
                row.getProjectId(),
                row.getProjectTitle(),
                row.getClientName(),
                row.getFreelancerName(),
                status,
                status.getLabel(),
                row.getTotalRound() == null ? 0 : row.getTotalRound(),
                row.getStartedAt(),
                row.getEndedAt());
    }
}
