package com.pairing.admin.project.presentation.api.response;

import com.pairing.admin.project.domain.ProjectCodeLabels;
import com.pairing.admin.project.domain.ProjectNo;
import com.pairing.admin.project.domain.ProjectPaymentStatus;
import com.pairing.admin.project.domain.ProjectStatus;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.ListRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 프로젝트 목록 한 줄. 피그마의 표 컬럼(번호·프로젝트명·클라이언트·상태·계약 금액·등록일)과 대응한다.
 *
 * <p><b>금액이 두 개인 이유.</b> 계약 전 프로젝트에는 계약금액이 없다. 화면은
 * {@code contractSalaryAmount} 가 있으면 "월 5,000,000원", 없으면 {@code budgetAmount} 를
 * "예산 30,000,000원" 으로 그린다. <b>두 값은 단위가 다르다</b> — 계약금액은 월 단가,
 * 예산은 총액이다. 예산에 "월" 을 붙이면 6배쯤 틀린 금액이 화면에 찍힌다.
 */
@Schema(description = "프로젝트 목록 항목")
public record ProjectSummaryResponse(

        @Schema(description = "프로젝트 ID. 상세 조회 키다.", example = "1")
        Long projectId,

        @Schema(description = "화면 표시용 번호. 저장된 값이 아니라 ID 로 만든 표기다.",
                example = "PRJ-001")
        String projectNo,

        @Schema(description = "프로젝트명", example = "쇼핑몰 관리자 페이지 리뉴얼")
        String title,

        @Schema(description = "클라이언트 계정 ID. 회원 상세로 넘어가는 링크에 쓴다.", example = "12")
        Long clientAccountId,

        @Schema(description = "클라이언트 회사명", example = "삼성전자")
        String clientName,

        @Schema(description = "프로젝트 상태. 모르는 코드면 null 이고 statusCode 에 원문이 온다.",
                example = "IN_PROGRESS")
        ProjectStatus status,

        @Schema(description = "DB 에 저장된 상태 코드 원문", example = "IN_PROGRESS")
        String statusCode,

        @Schema(description = "상태 표시명", example = "진행중")
        String statusLabel,

        @Schema(description = "결제 상태", example = "DEPOSIT_PAID")
        ProjectPaymentStatus paymentStatus,

        @Schema(description = "결제 상태 표시명", example = "착수금 결제 완료")
        String paymentStatusLabel,

        @Schema(description = "대표 포지션의 직군 코드. 포지션이 없으면 null", example = "DEVELOPMENT")
        String jobCategory,

        @Schema(description = "직군 표시명", example = "개발")
        String jobCategoryLabel,

        @Schema(description = "대표 포지션의 직무 코드", example = "FRONTEND")
        String jobRole,

        @Schema(description = "직무 표시명", example = "프론트엔드 개발자")
        String jobRoleLabel,

        @Schema(description = "모집 포지션 개수. 2 이상이면 화면에서 \"외 N건\" 으로 덧붙인다.",
                example = "1")
        long positionCount,

        @Schema(description = "계약 월 단가(원). 계약 전이면 null", example = "5000000")
        Long contractSalaryAmount,

        @Schema(description = "등록 시 예산(원). <b>총액</b>이며 월 단가가 아니다.", example = "30000000")
        Long budgetAmount,

        @Schema(description = "예상 기간 값", example = "6")
        Integer periodValue,

        @Schema(description = "예상 기간 단위", example = "MONTH")
        String periodUnit,

        @Schema(description = "기간 단위 표시명", example = "개월")
        String periodUnitLabel,

        @Schema(description = "모집 인원", example = "3")
        Integer totalHeadcount,

        @Schema(description = "확정 인원", example = "1")
        Integer confirmedHeadcount,

        @Schema(description = "등록일")
        LocalDateTime createdAt
) {

    public static ProjectSummaryResponse from(ListRow row) {
        return new ProjectSummaryResponse(
                row.getProjectId(),
                ProjectNo.of(row.getProjectId()),
                row.getTitle(),
                row.getClientAccountId(),
                row.getClientName(),
                ProjectStatus.find(row.getStatus()),
                row.getStatus(),
                ProjectStatus.labelOf(row.getStatus()),
                ProjectPaymentStatus.find(row.getPaymentStatus()),
                ProjectPaymentStatus.labelOf(row.getPaymentStatus()),
                row.getJobCategory(),
                ProjectCodeLabels.labelOf(ProjectCodeLabels.JOB_CATEGORY, row.getJobCategory()),
                row.getJobRole(),
                ProjectCodeLabels.labelOf(ProjectCodeLabels.JOB_ROLE, row.getJobRole()),
                row.getPositionCount(),
                row.getContractSalaryAmount(),
                row.getBudgetAmount(),
                row.getPeriodValue(),
                row.getPeriodUnit(),
                ProjectCodeLabels.labelOf(ProjectCodeLabels.PERIOD_UNIT, row.getPeriodUnit()),
                row.getTotalHeadcount(),
                row.getConfirmedHeadcount(),
                row.getCreatedAt()
        );
    }
}
