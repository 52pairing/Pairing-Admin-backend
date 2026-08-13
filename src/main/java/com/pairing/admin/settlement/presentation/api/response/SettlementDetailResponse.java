package com.pairing.admin.settlement.presentation.api.response;

import com.pairing.admin.project.domain.ProjectNo;
import com.pairing.admin.project.domain.ProjectStatus;
import com.pairing.admin.settlement.domain.PayerRole;
import com.pairing.admin.settlement.domain.SettlementPhase;
import com.pairing.admin.settlement.domain.SettlementStatus;
import com.pairing.admin.settlement.infrastructure.persistence.SettlementAdminQueryRepository.DetailRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 정산 상세.
 *
 * <p>목록 값에 <b>왜 이 상태인지</b>를 설명하는 값을 더한다 — 승인번호, 실패 사유, 미납 사유.
 * 관리자가 정산 화면을 여는 대부분의 이유가 "왜 결제가 안 됐는가" 라서, 그 답이 상세에 없으면
 * 결국 DB 를 직접 열어 보게 된다.
 *
 * <p>결제수단은 <b>ID 만</b> 내려 준다. 카드사·마스킹 번호는 회원 소유의 정보라, 정산 확인에
 * 필요하지 않은 값을 관리자 화면으로 끌어오지 않는다.
 */
@Schema(description = "정산 상세")
public record SettlementDetailResponse(

        @Schema(description = "정산 ID", example = "31")
        Long settlementId,

        @Schema(description = "정산번호", example = "ST-2026-0031")
        String settlementNo,

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "화면 표시용 프로젝트 번호. 저장된 값이 아니다.", example = "PRJ-001")
        String projectNo,

        @Schema(description = "프로젝트명", example = "쇼핑몰 관리자 페이지 리뉴얼")
        String projectTitle,

        @Schema(description = "프로젝트 상태 표시명", example = "진행중")
        String projectStatusLabel,

        @Schema(description = "계약 ID. 착수금 정산 중 클라이언트 건은 계약 전에 생기므로 null 일 수 있다.",
                example = "3")
        Long contractId,

        @Schema(description = "계약번호", example = "CT-2026-0003")
        String contractNo,

        @Schema(description = "납부자 계정 ID", example = "12")
        Long payerAccountId,

        @Schema(description = "화면에 표시할 회원명", example = "삼성전자")
        String memberName,

        @Schema(description = "납부자 이름(account.name)", example = "홍길동")
        String payerName,

        @Schema(description = "납부자 이메일", example = "hong@example.com")
        String payerEmail,

        @Schema(description = "납부자 연락처", example = "01012345678")
        String payerPhone,

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

        @Schema(description = "기준금액(원)", example = "30000000")
        Long baseAmount,

        @Schema(description = "기본 수수료율(%)", example = "3.00")
        BigDecimal feeRate,

        @Schema(description = "등급 할인(%p)", example = "1.00")
        BigDecimal gradeDiscount,

        @Schema(description = "실제 적용 요율(%)", example = "2.00")
        BigDecimal effectiveFeeRate,

        @Schema(description = "수수료(원)", example = "600000")
        Long feeAmount,

        @Schema(description = "정산 상태", example = "FAILED")
        SettlementStatus status,

        @Schema(description = "DB 에 저장된 상태 코드 원문", example = "FAILED")
        String statusCode,

        @Schema(description = "상태 표시명", example = "결제 실패")
        String statusLabel,

        @Schema(description = "납부 기한")
        LocalDate dueDate,

        @Schema(description = "완료일(결제 완료 시각)")
        LocalDateTime paidAt,

        @Schema(description = "결제 승인번호. 결제 완료 건만 값이 있다.", example = "00123456")
        String approvalNo,

        @Schema(description = "결제 실패 사유", example = "한도 초과")
        String failReason,

        @Schema(description = "미납 사유")
        String overdueReason,

        @Schema(description = "결제수단 ID. 상세 정보는 회원 소유라 펼치지 않는다.", example = "8")
        Long paymentMethodId,

        @Schema(description = "정산 생성일")
        LocalDateTime createdAt
) {

    public static SettlementDetailResponse from(DetailRow row) {
        PayerRole payerRole = PayerRole.find(row.getPayerRole());

        return new SettlementDetailResponse(
                row.getSettlementId(),
                row.getSettlementNo(),
                row.getProjectId(),
                ProjectNo.of(row.getProjectId()),
                row.getProjectTitle(),
                ProjectStatus.labelOf(row.getProjectStatus()),
                row.getContractId(),
                row.getContractNo(),
                row.getPayerAccountId(),
                SettlementSummaryResponse.memberName(payerRole, row.getCompanyName(), row.getPayerName()),
                row.getPayerName(),
                row.getPayerEmail(),
                row.getPayerPhone(),
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
                row.getApprovalNo(),
                row.getFailReason(),
                row.getOverdueReason(),
                row.getPaymentMethodId(),
                row.getCreatedAt()
        );
    }
}
