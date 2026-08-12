package com.pairing.admin.negotiation.presentation.api.response;

import com.pairing.admin.negotiation.infrastructure.persistence.NegotiationAdminRepository;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AI Agent 관리 목록 상단 카드.
 *
 * <p>평균값은 소수점 첫째 자리에서 끊는다. 화면 카드에 {@code 3.4285714285} 같은 값이 그대로
 * 찍히지 않게 <b>서버에서 자른다</b> — 프론트마다 반올림 규칙이 갈리면 같은 수치가 화면별로
 * 다르게 보인다.
 */
@Schema(description = "협상 요약 집계")
public record NegotiationSummaryResponse(

        @Schema(description = "전체 협상 세션 수", example = "42")
        long total,

        @Schema(description = "진행 중", example = "7")
        long inProgress,

        @Schema(description = "타결", example = "30")
        long agreed,

        @Schema(description = "결렬", example = "5")
        long failed,

        @Schema(description = "평균 라운드 수", example = "3.4")
        double averageRound,

        @Schema(description = "평균 소요 일수(종료된 협상 기준)", example = "1.8")
        double averageDurationDays
) {

    public static NegotiationSummaryResponse from(NegotiationAdminRepository.SummaryRow row) {
        return new NegotiationSummaryResponse(
                row.getTotal(),
                row.getInProgress(),
                row.getAgreed(),
                row.getFailed(),
                round1(row.getAverageRound()),
                round1(row.getAverageDurationDays()));
    }

    /** 대상이 없으면 집계가 NULL 로 오므로 0 으로 바꾼다(협상이 0건이거나 종료된 협상이 없을 때). */
    private static double round1(Double value) {
        if (value == null) {
            return 0d;
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
