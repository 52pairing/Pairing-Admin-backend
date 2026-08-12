package com.pairing.admin.review.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/** 요약 카드 6개와 별점 분포 그래프에 쓰는 값. */
@Schema(description = "사이트 리뷰 요약")
public record SiteReviewSummaryResponse(

        @Schema(description = "평균 별점. 후기가 없으면 0", example = "4.8")
        double ratingAverage,

        @Schema(description = "전체 리뷰", example = "4")
        long totalCount,

        @Schema(description = "이번 달 작성", example = "1")
        long thisMonthCount,

        @Schema(description = "홍보 활용", example = "2")
        long promotedCount,

        @Schema(description = "홍보 제외", example = "2")
        long notPromotedCount,

        @Schema(description = "공개", example = "3")
        long publicCount,

        @Schema(description = "별점 분포. 5~1 을 모두 담고, 0건인 별점도 0 으로 내려간다.",
                example = "{\"5\":2,\"4\":1,\"3\":1,\"2\":0,\"1\":0}")
        Map<Integer, Long> scoreDistribution
) {
}
