package com.pairing.admin.support.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "1:1 문의 요약 카드 (목록 상단)")
public record InquirySummaryResponse(

        @Schema(description = "전체 문의", example = "4")
        long totalCount,

        @Schema(description = "답변 대기", example = "2")
        long pendingCount,

        @Schema(description = "답변 완료", example = "2")
        long answeredCount,

        @Schema(description = "오늘 접수", example = "1")
        long todayCount
) {
}
