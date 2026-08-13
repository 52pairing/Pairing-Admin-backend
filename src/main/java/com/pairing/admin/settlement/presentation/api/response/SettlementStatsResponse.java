package com.pairing.admin.settlement.presentation.api.response;

import com.pairing.admin.settlement.infrastructure.persistence.SettlementAdminQueryRepository.PenaltyRow;
import com.pairing.admin.settlement.infrastructure.persistence.SettlementAdminQueryRepository.SummaryRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.YearMonth;

/**
 * 거래·정산 관리 목록 상단의 요약 카드 6개. (요구사항 R39)
 *
 * <p>모든 금액은 <b>수수료</b> 기준이다. 기준금액({@code base_amount})은 거래 규모일 뿐
 * 플랫폼이 받는 돈이 아니라 카드에 쓰지 않는다.
 *
 * <p>취소된 정산은 어느 카드에도 들어가지 않는다. 그래서 카드 금액의 합이
 * 전체 정산 금액과 맞지 않는다 — 일부러 그렇다.
 */
@Schema(description = "정산 요약 카드")
public record SettlementStatsResponse(

        @Schema(description = "총 수수료 수익. 결제 완료된 수수료의 합", example = "125000000")
        long totalRevenue,

        @Schema(description = "이번 달 수익. 결제 완료 시각(paid_at) 기준", example = "18000000")
        long monthlyRevenue,

        @Schema(description = "집계 기준이 된 달. 서버(KST) 기준이다.", example = "2026-08")
        String revenueMonth,

        @Schema(description = "결제 예정 금액. 아직 내지 않았고 기한도 남은 건", example = "9000000")
        long pendingAmount,

        @Schema(description = "결제 예정 건수", example = "4")
        long pendingCount,

        @Schema(description = "미납 금액", example = "1500000")
        long overdueAmount,

        @Schema(description = "미납 건수", example = "2")
        long overdueCount,

        @Schema(description = "결제 실패 금액", example = "300000")
        long failedAmount,

        @Schema(description = "결제 실패 건수", example = "1")
        long failedCount,

        @Schema(description = "위약금 수수료(납부 완료분). "
                + "<b>백엔드에 Penalty 도메인이 아직 없어 현재는 항상 0 이다.</b>", example = "0")
        long penaltyPaidAmount,

        @Schema(description = "위약금 납부 완료 건수", example = "0")
        long penaltyPaidCount,

        @Schema(description = "위약금 납부 대기 금액", example = "0")
        long penaltyPendingAmount,

        @Schema(description = "위약금 납부 대기 건수", example = "0")
        long penaltyPendingCount
) {

    public static SettlementStatsResponse from(SummaryRow row, PenaltyRow penalty, YearMonth month) {
        return new SettlementStatsResponse(
                zeroIfNull(row.getTotalRevenue()),
                zeroIfNull(row.getMonthlyRevenue()),
                month.toString(),
                zeroIfNull(row.getPendingAmount()),
                row.getPendingCount(),
                zeroIfNull(row.getOverdueAmount()),
                row.getOverdueCount(),
                zeroIfNull(row.getFailedAmount()),
                row.getFailedCount(),
                zeroIfNull(penalty.getPaidAmount()),
                penalty.getPaidCount(),
                zeroIfNull(penalty.getPendingAmount()),
                penalty.getPendingCount()
        );
    }

    /**
     * 쿼리가 {@code COALESCE} 로 이미 0 을 만들지만 한 번 더 막는다.
     *
     * <p>합계 컬럼이 하나 추가되면서 {@code COALESCE} 를 빠뜨리면 금액 카드가 통째로
     * {@code NullPointerException} 으로 죽는다. 금액이 0 인 화면이 죽은 화면보다 낫다.
     */
    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}
