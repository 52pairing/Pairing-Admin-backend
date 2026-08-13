package com.pairing.admin.project.presentation.api.response;

import com.pairing.admin.project.domain.ContractStatus;
import com.pairing.admin.project.domain.ContractType;
import com.pairing.admin.project.domain.ProjectCodeLabels;
import com.pairing.admin.project.domain.ProjectNo;
import com.pairing.admin.project.domain.ProjectPaymentStatus;
import com.pairing.admin.project.domain.ProjectStatus;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.ContractRow;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.DetailRow;
import com.pairing.admin.project.infrastructure.persistence.ProjectAdminQueryRepository.PositionRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 프로젝트 상세. 피그마의 카드 두 개("프로젝트 기본 정보", "계약 정보")를 한 번에 내려 준다.
 *
 * <p>피그마가 한 줄로 그리는 값 중 <b>실제로는 여러 건일 수 있는 것</b>이 둘 있다.
 * <ul>
 *   <li>직군·직무 — 프로젝트는 포지션을 여러 개 모집할 수 있다({@code positions})</li>
 *   <li>매칭 프리랜서·계약 정보 — 인원이 여럿이면 계약도 여럿이다({@code contracts})</li>
 * </ul>
 * 화면이 첫 건만 그리더라도 서버가 하나로 줄여 버리면 관리자는 나머지가 있다는 사실조차
 * 알 수 없다. 그래서 배열로 내리고, 편의를 위해 대표 한 건을 함께 담는다.
 */
@Schema(description = "프로젝트 상세")
public record ProjectDetailResponse(

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "화면 표시용 번호. 저장된 값이 아니다.", example = "PRJ-001")
        String projectNo,

        @Schema(description = "프로젝트명", example = "쇼핑몰 관리자 페이지 리뉴얼")
        String title,

        @Schema(description = "클라이언트 계정 ID", example = "12")
        Long clientAccountId,

        @Schema(description = "클라이언트 회사명", example = "삼성전자")
        String clientName,

        @Schema(description = "클라이언트 담당자명. 회사명과 다른 값이다.", example = "홍길동")
        String clientManagerName,

        @Schema(description = "클라이언트 담당자 이메일", example = "hong@example.com")
        String clientEmail,

        @Schema(description = "클라이언트 담당자 연락처", example = "01012345678")
        String clientPhone,

        @Schema(description = "프로젝트 상태", example = "IN_PROGRESS")
        ProjectStatus status,

        @Schema(description = "DB 에 저장된 상태 코드 원문", example = "IN_PROGRESS")
        String statusCode,

        @Schema(description = "상태 표시명", example = "진행중")
        String statusLabel,

        @Schema(description = "결제 상태", example = "DEPOSIT_PAID")
        ProjectPaymentStatus paymentStatus,

        @Schema(description = "결제 상태 표시명", example = "착수금 결제 완료")
        String paymentStatusLabel,

        @Schema(description = "예산(원). <b>총액</b>이다. 월 단가가 아니다.", example = "30000000")
        Long budgetAmount,

        @Schema(description = "예상 기간 값", example = "6")
        Integer periodValue,

        @Schema(description = "예상 기간 단위", example = "MONTH")
        String periodUnit,

        @Schema(description = "기간 단위 표시명", example = "개월")
        String periodUnitLabel,

        @Schema(description = "근무 방식 코드", example = "ANY")
        String workStyle,

        @Schema(description = "근무 방식 표시명. ANY 는 \"혼합\" 이다.", example = "혼합")
        String workStyleLabel,

        @Schema(description = "근무 형태 코드", example = "FULL_TIME")
        String workForm,

        @Schema(description = "근무 형태 표시명", example = "풀타임")
        String workFormLabel,

        @Schema(description = "근무지. 재택이면 비어 있을 수 있다.", example = "서울 강남구")
        String workLocation,

        @Schema(description = "계약유형. <b>DB 에 없는 고정값</b>이다.", example = "프리랜서")
        String contractType,

        @Schema(description = "희망 시작일")
        LocalDate startDesiredDate,

        @Schema(description = "시작일 협의 가능 여부", example = "true")
        Boolean startNegotiable,

        @Schema(description = "모집 인원", example = "3")
        Integer totalHeadcount,

        @Schema(description = "확정 인원", example = "1")
        Integer confirmedHeadcount,

        @Schema(description = "현재 상황(클라이언트 작성)")
        String currentSituation,

        @Schema(description = "주요 업무")
        String mainTask,

        @Schema(description = "상세 범위")
        String detailScope,

        @Schema(description = "기타 참고사항")
        String extraNote,

        @Schema(description = "모집 시작 시각")
        LocalDateTime recruitStartedAt,

        @Schema(description = "모집 마감 시각")
        LocalDateTime recruitDeadline,

        @Schema(description = "모집 연장 횟수", example = "0")
        Integer extensionCount,

        @Schema(description = "유의사항 동의 시각")
        LocalDateTime noticeAgreedAt,

        @Schema(description = "취소 시각. 취소된 프로젝트만 값이 있다.")
        LocalDateTime canceledAt,

        @Schema(description = "종료 시각")
        LocalDateTime closedAt,

        @Schema(description = "등록일")
        LocalDateTime createdAt,

        @Schema(description = "모집 포지션. 등록 직후라면 비어 있을 수 있다.")
        List<Position> positions,

        @Schema(description = "계약. 서명 거부된 건도 이력으로 포함한다. 계약 전이면 빈 배열")
        List<Contract> contracts
) {

    @Schema(description = "모집 포지션 하나")
    public record Position(

            @Schema(description = "포지션 ID", example = "5")
            Long positionId,

            @Schema(description = "포지션 번호. 프로젝트 안에서의 순번이다.", example = "1")
            Integer positionNo,

            @Schema(description = "직군 코드", example = "DEVELOPMENT")
            String jobCategory,

            @Schema(description = "직군 표시명", example = "개발")
            String jobCategoryLabel,

            @Schema(description = "직무 코드", example = "FRONTEND")
            String jobRole,

            @Schema(description = "직무 표시명", example = "프론트엔드 개발자")
            String jobRoleLabel,

            @Schema(description = "최소 경력(년)", example = "3")
            Integer minCareerYears,

            @Schema(description = "모집 인원", example = "2")
            Integer headcount,

            @Schema(description = "확정 인원", example = "1")
            Integer confirmedCount,

            @Schema(description = "포지션 상태 코드", example = "RECRUITING")
            String status,

            @Schema(description = "포지션 상태 표시명", example = "모집중")
            String statusLabel,

            @Schema(description = "우대사항")
            String preferredNote,

            @Schema(description = "요구 기술 스택 코드. 없으면 빈 배열",
                    example = "[\"REACT\", \"TYPESCRIPT\"]")
            List<String> skillCodes
    ) {

        static Position from(PositionRow row) {
            return new Position(
                    row.getPositionId(),
                    row.getPositionNo(),
                    row.getJobCategory(),
                    ProjectCodeLabels.labelOf(ProjectCodeLabels.JOB_CATEGORY, row.getJobCategory()),
                    row.getJobRole(),
                    ProjectCodeLabels.labelOf(ProjectCodeLabels.JOB_ROLE, row.getJobRole()),
                    row.getMinCareerYears(),
                    row.getHeadcount(),
                    row.getConfirmedCount(),
                    row.getStatus(),
                    ProjectCodeLabels.labelOf(ProjectCodeLabels.POSITION_STATUS, row.getStatus()),
                    row.getPreferredNote(),
                    splitSkills(row.getSkillCodes()));
        }

        /** {@code string_agg} 결과를 나눈다. 스택이 하나도 없으면 null 이 온다. */
        private static List<String> splitSkills(String joined) {
            if (joined == null || joined.isBlank()) {
                return List.of();
            }
            return Arrays.stream(joined.split(","))
                    .map(String::trim)
                    .filter(code -> !code.isEmpty())
                    .toList();
        }
    }

    @Schema(description = "계약 한 건. 매칭 프리랜서 정보를 함께 담는다.")
    public record Contract(

            @Schema(description = "계약 ID", example = "3")
            Long contractId,

            @Schema(description = "계약번호. <b>이쪽은 실제로 채번된 값</b>이다.",
                    example = "CT-2026-0003")
            String contractNo,

            @Schema(description = "이 계약이 채운 포지션 ID", example = "5")
            Long positionId,

            @Schema(description = "매칭 프리랜서 계정 ID", example = "77")
            Long freelancerAccountId,

            @Schema(description = "매칭 프리랜서 이름", example = "김프리")
            String freelancerName,

            @Schema(description = "매칭 프리랜서 이메일", example = "kim@example.com")
            String freelancerEmail,

            @Schema(description = "계약 월 단가(원)", example = "5000000")
            Long salaryAmount,

            @Schema(description = "총 계약금액(원). 월 단가 × 계약 개월 수", example = "15000000")
            Long totalAmount,

            @Schema(description = "계약 시작일")
            LocalDate startDate,

            @Schema(description = "계약 종료일")
            LocalDate endDate,

            @Schema(description = "계약 상태", example = "IN_PROGRESS")
            ContractStatus status,

            @Schema(description = "DB 에 저장된 계약 상태 코드 원문", example = "IN_PROGRESS")
            String statusCode,

            @Schema(description = "계약 상태 표시명. 프로젝트 상태와 라벨 체계가 다르다.",
                    example = "진행중")
            String statusLabel,

            @Schema(description = "계약서상 근무 방식 표시명", example = "혼합")
            String workStyleLabel,

            @Schema(description = "계약서상 근무 형태 표시명", example = "풀타임")
            String workFormLabel,

            @Schema(description = "양측 서명 완료 시각")
            LocalDateTime signedAt,

            @Schema(description = "계약 완료 시각")
            LocalDateTime completedAt,

            @Schema(description = "중도 파기 시각")
            LocalDateTime terminatedAt,

            @Schema(description = "파기 주체", example = "CLIENT")
            String terminatedBy,

            @Schema(description = "계약 생성 시각")
            LocalDateTime createdAt
    ) {

        static Contract from(ContractRow row) {
            return new Contract(
                    row.getContractId(),
                    row.getContractNo(),
                    row.getPositionId(),
                    row.getFreelancerAccountId(),
                    row.getFreelancerName(),
                    row.getFreelancerEmail(),
                    row.getSalaryAmount(),
                    row.getTotalAmount(),
                    row.getStartDate(),
                    row.getEndDate(),
                    ContractStatus.find(row.getStatus()),
                    row.getStatus(),
                    ContractStatus.labelOf(row.getStatus()),
                    ProjectCodeLabels.labelOf(ProjectCodeLabels.WORK_STYLE, row.getWorkStyle()),
                    ProjectCodeLabels.labelOf(ProjectCodeLabels.WORK_FORM, row.getWorkForm()),
                    row.getSignedAt(),
                    row.getCompletedAt(),
                    row.getTerminatedAt(),
                    row.getTerminatedBy(),
                    row.getCreatedAt());
        }
    }

    public static ProjectDetailResponse from(DetailRow row,
                                             List<PositionRow> positions,
                                             List<ContractRow> contracts) {
        return new ProjectDetailResponse(
                row.getProjectId(),
                ProjectNo.of(row.getProjectId()),
                row.getTitle(),
                row.getClientAccountId(),
                row.getClientName(),
                row.getClientManagerName(),
                row.getClientEmail(),
                row.getClientPhone(),
                ProjectStatus.find(row.getStatus()),
                row.getStatus(),
                ProjectStatus.labelOf(row.getStatus()),
                ProjectPaymentStatus.find(row.getPaymentStatus()),
                ProjectPaymentStatus.labelOf(row.getPaymentStatus()),
                row.getBudgetAmount(),
                row.getPeriodValue(),
                row.getPeriodUnit(),
                ProjectCodeLabels.labelOf(ProjectCodeLabels.PERIOD_UNIT, row.getPeriodUnit()),
                row.getWorkStyle(),
                ProjectCodeLabels.labelOf(ProjectCodeLabels.WORK_STYLE, row.getWorkStyle()),
                row.getWorkForm(),
                ProjectCodeLabels.labelOf(ProjectCodeLabels.WORK_FORM, row.getWorkForm()),
                row.getWorkLocation(),
                ContractType.FREELANCE,
                row.getStartDesiredDate(),
                row.getStartNegotiable(),
                row.getTotalHeadcount(),
                row.getConfirmedHeadcount(),
                row.getCurrentSituation(),
                row.getMainTask(),
                row.getDetailScope(),
                row.getExtraNote(),
                row.getRecruitStartedAt(),
                row.getRecruitDeadline(),
                row.getExtensionCount(),
                row.getNoticeAgreedAt(),
                row.getCanceledAt(),
                row.getClosedAt(),
                row.getCreatedAt(),
                positions.stream().map(Position::from).toList(),
                contracts.stream().map(Contract::from).toList()
        );
    }
}
