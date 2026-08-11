package com.pairing.admin.dashboard.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "관리자 대시보드")
public record AdminDashboardResponse(

        @Schema(description = "기준 일자")
        LocalDate baseDate,

        Members members,
        Projects projects,
        Negotiations negotiations,
        Settlements settlements,

        @Schema(description = "처리 대기 항목")
        List<PendingItem> pendingItems
) {

    @Schema(description = "회원 지표")
    public record Members(
            @Schema(description = "전체 회원 수") long total,
            @Schema(description = "오늘 가입") long joinedToday,
            @Schema(description = "정지 상태") long locked
    ) {}

    @Schema(description = "프로젝트 지표")
    public record Projects(
            @Schema(description = "전체") long total,
            @Schema(description = "모집 중") long recruiting,
            @Schema(description = "진행 중") long inProgress
    ) {}

    @Schema(description = "협상 지표")
    public record Negotiations(
            @Schema(description = "진행 중") long inProgress,
            @Schema(description = "성사") long agreed,
            @Schema(description = "결렬") long broken
    ) {}

    @Schema(description = "정산 지표")
    public record Settlements(
            @Schema(description = "누적 거래액(원)") long totalAmount,
            @Schema(description = "이번 달 거래액(원)") long monthlyAmount,
            @Schema(description = "미납 금액(원)") long unpaidAmount
    ) {}

    @Schema(description = "처리 대기 항목")
    public record PendingItem(
            @Schema(description = "종류", example = "INQUIRY") String type,
            @Schema(description = "표시명", example = "답변 대기 문의") String label,
            @Schema(description = "건수", example = "3") long count
    ) {}
}
