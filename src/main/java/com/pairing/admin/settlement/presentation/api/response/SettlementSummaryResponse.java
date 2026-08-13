package com.pairing.admin.settlement.presentation.api.response;

import com.pairing.admin.settlement.domain.PayerRole;
import com.pairing.admin.settlement.domain.SettlementPhase;
import com.pairing.admin.settlement.domain.SettlementStatus;
import com.pairing.admin.settlement.infrastructure.persistence.SettlementAdminQueryRepository.ListRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 목록 한 줄. R39 의 표 컬럼과 대응한다.
 *
 * <p>회원명({@code memberName})은 역할에 따라 출처가 다르다 — 클라이언트는 회사명,
 * 프리랜서는 이름이다. 화면이 매번 고르지 않아도 되도록 서버에서 정해서 내려 주고,
 * 원본 두 값도 함께 담는다.
 */
@Schema(description = "정산 목록 항목")
public record SettlementSummaryResponse(

        @Schema(description = "정산 ID. 상세 조회 키다.", example = "31")
        Long settlementId,

        @Schema(description = "정산번호. 채번된 실제 값이다.", example = "ST-2026-0031")
        String settlementNo,

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "프로젝트명", example = "쇼핑몰 관리자 페이지 리뉴얼")
        String projectTitle,

        @Schema(description = "납부자 계정 ID", example = "12")
        Long payerAccountId,

        @Schema(description = "화면에 표시할 회원명. 클라이언트면 회사명, 프리랜서면 이름",
                example = "삼성전자")
        String memberName,

        @Schema(description = "납부자 이름(account.name). 클라이언트면 담당자명", example = "홍길동")
        String payerName,

        @Schema(description = "회사명. 클라이언트만 값이 있다.", example = "삼성전자")
        String companyName,

        @Schema(description = "납부자 구분", example = "CLIENT")
        PayerRole payerRole,

        @Schema(description = "납부자 구분 표시명", example = "클라이언트")
        String payerRoleLabel,

        @Schema(description = "수수료 유형", example = "DEPOSIT")
        SettlementPhase phase,

        @Schema(description = "수수료 유형 표시명", example = "착수금 수수료")
        String phaseLabel,

        @Schema(description = "기준금액(원). 프로젝트 예산 또는 계약 총액", example = "30000000")
        Long baseAmount,

        @Schema(description = "기본 수수료율(%)", example = "3.00")
        BigDecimal feeRate,

        @Schema(description = "등급 할인(%p). 비율이 아니라 요율에서 빼는 값이다.", example = "1.00")
        BigDecimal gradeDiscount,

        @Schema(description = "실제 적용 요율(%) = 수수료율 - 등급 할인", example = "2.00")
        BigDecimal effectiveFeeRate,

        @Schema(description = "수수료(원). 원 단위 미만은 버린다.", example = "600000")
        Long feeAmount,

        @Schema(description = "정산 상태", example = "PAID")
        SettlementStatus status,

        @Schema(description = "DB 에 저장된 상태 코드 원문", example = "PAID")
        String statusCode,

        @Schema(description = "상태 표시명", example = "결제 완료")
        String statusLabel,

        @Schema(description = "납부 기한")
        LocalDate dueDate,

        @Schema(description = "완료일(결제 완료 시각). 미납·대기면 null")
        LocalDateTime paidAt,

        @Schema(description = "정산 생성일")
        LocalDateTime createdAt
) {

    public static SettlementSummaryResponse from(ListRow row) {
        PayerRole payerRole = PayerRole.find(row.getPayerRole());

        return new SettlementSummaryResponse(
                row.getSettlementId(),
                row.getSettlementNo(),
                row.getProjectId(),
                row.getProjectTitle(),
                row.getPayerAccountId(),
                memberName(payerRole, row.getCompanyName(), row.getPayerName()),
                row.getPayerName(),
                row.getCompanyName(),
                payerRole,
                PayerRole.labelOf(row.getPayerRole()),
                SettlementPhase.find(row.getPhase()),
                SettlementPhase.labelOf(row.getPhase()),
                row.getBaseAmount(),
                row.getFeeRate(),
                row.getGradeDiscount(),
                row.getEffectiveFeeRate(),
                row.getFeeAmount(),
                SettlementStatus.find(row.getStatus()),
                row.getStatus(),
                SettlementStatus.labelOf(row.getStatus()),
                row.getDueDate(),
                row.getPaidAt(),
                row.getCreatedAt()
        );
    }

    /**
     * 표에 찍을 이름 하나를 고른다.
     *
     * <p>클라이언트인데 회사명이 없으면(프로필이 지워진 과거 데이터) 담당자명으로 물러난다.
     * 빈 칸을 내보내면 관리자가 어느 회원의 미납인지 알 수 없다.
     */
    static String memberName(PayerRole payerRole, String companyName, String payerName) {
        if (payerRole == PayerRole.CLIENT && companyName != null && !companyName.isBlank()) {
            return companyName;
        }
        return payerName;
    }
}
