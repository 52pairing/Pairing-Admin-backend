package com.pairing.admin.member.presentation.api.response;

import com.pairing.admin.member.domain.MemberCodeLabels;
import com.pairing.admin.member.domain.MemberStatusFilter;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.domain.SignupMethod;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaEntity;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.ProfileRow;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.SkillRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 회원 상세.
 *
 * <p>화면 구성을 그대로 따라 세 덩어리로 나눈다 — 기본 정보 · 프로필 · 활동 현황.
 * 프로필은 역할에 따라 채워지는 필드가 다르고, 활동 현황은 집계라 계산 비용이 다르다.
 * 한 겹 안에 다 늘어놓으면 프런트가 어떤 값이 언제 null 인지 알기 어렵다.
 */
@Schema(description = "회원 상세")
public record MemberDetailResponse(

        // ---------- 기본 정보 ----------

        @Schema(description = "계정 ID. 화면의 회원번호로 그대로 쓴다.", example = "12")
        Long accountId,

        String name,
        String email,
        String phone,

        Role role,
        String roleLabel,

        @Schema(description = "화면에 표시할 상태. 정지가 계정 상태보다 우선한다.")
        MemberStatusFilter status,
        String statusLabel,

        @Schema(description = "정지 여부")
        boolean suspended,

        @Schema(description = "정지 시각. 정지 중이 아니면 null")
        LocalDateTime suspendedAt,

        @Schema(description = "정지 사유")
        String suspendReason,

        SignupMethod signupMethod,
        String signupMethodLabel,

        @Schema(description = "이메일 인증 완료 여부")
        boolean emailVerified,

        @Schema(description = "비밀번호 연속 실패 횟수")
        int loginFailCount,

        @Schema(description = "비밀번호 5회 실패로 자동 잠긴 시각. 관리자 정지와는 다르다.")
        LocalDateTime lockedAt,

        @Schema(description = "최근 로그인")
        LocalDateTime lastLoginAt,

        @Schema(description = "가입일")
        LocalDateTime createdAt,

        @Schema(description = "정보 수정일")
        LocalDateTime updatedAt,

        @Schema(description = "탈퇴 시각")
        LocalDateTime withdrawnAt,

        @Schema(description = "탈퇴 사유")
        String withdrawReason,

        // ---------- 프로필 / 활동 현황 ----------

        @Schema(description = "역할별 프로필. 해당 역할의 프로필이 없으면 null")
        Profile profile,

        @Schema(description = "활동 현황 집계")
        Activity activity
) {

    /**
     * 역할별 프로필.
     *
     * <p>클라이언트 필드와 프리랜서 필드가 한 레코드에 같이 있다. 역할에 해당하지 않는 쪽은
     * 전부 null 이며, 그게 정상이다. 프리랜서에게 사업자번호가 없고 클라이언트에게 생년월일이 없다.
     *
     * <p>{@code grade} 는 <b>역할마다 값 체계가 다르다</b>(클라이언트 실버/골드/다이아,
     * 프리랜서 주니어/시니어/마스터). 그래서 라벨을 서버가 만들어 준다. 프런트가 매핑하면
     * 역할을 잘못 짚었을 때 조용히 틀린 등급이 표시된다.
     */
    @Schema(description = "역할별 프로필")
    public record Profile(

            @Schema(description = "[클라이언트] 기업명", example = "삼성전자")
            String companyName,

            @Schema(description = "[클라이언트] 사업자등록번호", example = "1248100998")
            String businessNo,

            @Schema(description = "[클라이언트] 사업분야", example = "IT·정보통신")
            String businessField,

            @Schema(description = "[클라이언트] 직원수", example = "100~299명")
            String employeeCount,

            @Schema(description = "[프리랜서] 생년월일")
            LocalDate birthDate,

            @Schema(description = "주소. 역할에 맞는 프로필에서 가져온다.")
            String address,

            @Schema(description = "등급 코드", example = "SILVER")
            String grade,

            @Schema(description = "등급 표시명", example = "실버")
            String gradeLabel,

            @Schema(description = "[프리랜서] AI 매칭 동의 여부")
            Boolean aiMatchingAgreed,

            @Schema(description = "[프리랜서] 매칭 일시중지 여부")
            Boolean matchingPaused,

            @Schema(description = "[프리랜서] 근무 조건. 아직 등록하지 않았으면 null")
            Condition condition,

            @Schema(description = "[프리랜서] 기술 스택. 없으면 빈 배열")
            List<Skill> skills
    ) {

        /**
         * @return 해당 역할의 프로필 행이 아예 없으면 null. 가입 도중 이탈했거나
         *         프로필 생성 전인 계정이 여기 해당한다.
         */
        static Profile of(Role role, ProfileRow row, List<SkillRow> skillRows) {
            if (row == null) {
                return null;
            }
            if (role == Role.CLIENT) {
                if (row.getCompanyName() == null) {
                    return null;
                }
                return new Profile(
                        row.getCompanyName(),
                        row.getBusinessNo(),
                        row.getBusinessField(),
                        row.getEmployeeCount(),
                        null,
                        row.getClientAddress(),
                        row.getClientGrade(),
                        MemberCodeLabels.labelOf(MemberCodeLabels.CLIENT_GRADE, row.getClientGrade()),
                        null, null, null, null);
            }
            if (row.getBirthDate() == null) {
                return null;
            }
            return new Profile(
                    null, null, null, null,
                    row.getBirthDate(),
                    row.getFreelancerAddress(),
                    row.getFreelancerGrade(),
                    MemberCodeLabels.labelOf(MemberCodeLabels.FREELANCER_GRADE, row.getFreelancerGrade()),
                    row.getAiMatchingAgreed(),
                    row.getMatchingPaused(),
                    Condition.of(row),
                    Skill.of(skillRows));
        }
    }

    /**
     * 프리랜서가 등록한 근무 조건. ({@code freelancer_condition})
     *
     * <p>가입만 하고 조건을 아직 등록하지 않은 프리랜서가 있다. 그 경우 이 객체 자체가 null 이다.
     * 조건이 없으면 AI 매칭 대상이 아니므로, 관리 화면에서는 "조건 미등록" 으로 보여 주면 된다.
     */
    @Schema(description = "[프리랜서] 근무 조건")
    public record Condition(

            @Schema(description = "직군 코드", example = "DEVELOPMENT")
            String jobCategory,

            @Schema(description = "직군 표시명", example = "개발")
            String jobCategoryLabel,

            @Schema(description = "직무 코드", example = "BACKEND")
            String jobRole,

            @Schema(description = "직무 표시명", example = "백엔드 개발자")
            String jobRoleLabel,

            @Schema(description = "소속. 프리랜서가 적지 않으면 null", example = "무소속")
            String affiliation,

            @Schema(description = "경력 연수", example = "5")
            Integer careerYears,

            @Schema(description = "프리랜서 경험 보유 여부")
            Boolean hasFreelanceExperience,

            @Schema(description = "근무 방식 코드", example = "REMOTE")
            String workStyle,

            @Schema(description = "근무 방식 표시명", example = "재택")
            String workStyleLabel,

            @Schema(description = "근무 형태 코드", example = "FULL_TIME")
            String workForm,

            @Schema(description = "근무 형태 표시명", example = "풀타임")
            String workFormLabel,

            @Schema(description = "희망 단가 단위 코드", example = "MONTHLY")
            String payUnit,

            @Schema(description = "희망 단가 단위 표시명", example = "월급")
            String payUnitLabel,

            @Schema(description = "희망 단가", example = "5000000")
            Long payAmount,

            @Schema(description = "수용 가능한 최소 금액. 없으면 null", example = "4000000")
            Long minAcceptAmount,

            @Schema(description = "투입 가능일. 없으면 null")
            LocalDate availableFrom,

            @Schema(description = "시작일 협의 가능 여부")
            Boolean startNegotiable,

            @Schema(description = "희망 기간 값", example = "6")
            Integer periodValue,

            @Schema(description = "희망 기간 단위 코드", example = "MONTH")
            String periodUnit,

            @Schema(description = "희망 기간 단위 표시명", example = "개월")
            String periodUnitLabel
    ) {

        static Condition of(ProfileRow row) {
            // 조건 테이블이 LEFT JOIN 이라 미등록이면 전 컬럼이 null 이다.
            // NOT NULL 컬럼인 job_role 로 존재 여부를 판정한다.
            if (row.getJobRole() == null) {
                return null;
            }
            return new Condition(
                    row.getJobCategory(),
                    MemberCodeLabels.labelOf(MemberCodeLabels.JOB_CATEGORY, row.getJobCategory()),
                    row.getJobRole(),
                    MemberCodeLabels.labelOf(MemberCodeLabels.JOB_ROLE, row.getJobRole()),
                    row.getAffiliation(),
                    row.getCareerYears(),
                    row.getHasFreelanceExperience(),
                    row.getWorkStyle(),
                    MemberCodeLabels.labelOf(MemberCodeLabels.WORK_STYLE, row.getWorkStyle()),
                    row.getWorkForm(),
                    MemberCodeLabels.labelOf(MemberCodeLabels.WORK_FORM, row.getWorkForm()),
                    row.getPayUnit(),
                    MemberCodeLabels.labelOf(MemberCodeLabels.PAY_UNIT, row.getPayUnit()),
                    row.getPayAmount(),
                    row.getMinAcceptAmount(),
                    row.getAvailableFrom(),
                    row.getStartNegotiable(),
                    row.getPeriodValue(),
                    row.getPeriodUnit(),
                    MemberCodeLabels.labelOf(MemberCodeLabels.PERIOD_UNIT, row.getPeriodUnit()));
        }
    }

    @Schema(description = "[프리랜서] 기술 스택 항목")
    public record Skill(

            @Schema(description = "기술 코드", example = "SPRING_BOOT")
            String code,

            @Schema(description = "기술 표시명", example = "Spring Boot")
            String label,

            @Schema(description = "숙련도 코드", example = "ADVANCED")
            String level,

            @Schema(description = "숙련도 표시명", example = "고급")
            String levelLabel
    ) {

        static List<Skill> of(List<SkillRow> rows) {
            return rows.stream()
                    .map(r -> new Skill(
                            r.getSkillCode(),
                            MemberCodeLabels.labelOf(MemberCodeLabels.SKILL_CODE, r.getSkillCode()),
                            r.getSkillLevel(),
                            MemberCodeLabels.labelOf(MemberCodeLabels.SKILL_LEVEL, r.getSkillLevel())))
                    .toList();
        }
    }

    /** 피그마 상세 화면의 "활동 현황" 6개 지표. */
    @Schema(description = "활동 현황")
    public record Activity(

            @Schema(description = "진행중 프로젝트 (종료·취소가 아닌 전부)", example = "2")
            long inProgressProjects,

            @Schema(description = "완료 프로젝트", example = "8")
            long completedProjects,

            @Schema(description = "취소 프로젝트", example = "1")
            long canceledProjects,

            @Schema(description = "누적 거래금액. 결제 완료된 정산의 거래액 합계", example = "12500000")
            BigDecimal totalTradeAmount,

            @Schema(description = "받은 리뷰 수", example = "7")
            long reviewCount,

            @Schema(description = "받은 리뷰 평균 별점. 리뷰가 없으면 null", example = "4.6")
            Double averageScore
    ) {}

    public static MemberDetailResponse from(AccountJpaEntity account,
                                            ProfileRow profileRow,
                                            List<SkillRow> skillRows,
                                            Activity activity) {

        boolean suspended = account.isSuspended();
        MemberStatusFilter status = MemberStatusFilter.of(account.getStatus(), suspended);
        SignupMethod signupMethod = SignupMethod.of(
                account.getSignupType(), profileRow == null ? null : profileRow.getProvider());

        return new MemberDetailResponse(
                account.getId(),
                account.getName(),
                account.getEmail(),
                account.getPhone(),
                account.getRole(),
                account.getRole().getLabel(),
                status,
                status.getLabel(),
                suspended,
                account.getSuspendedAt(),
                account.getSuspendReason(),
                signupMethod,
                signupMethod.getLabel(),
                account.isEmailVerified(),
                account.getLoginFailCount(),
                account.getLockedAt(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                account.getWithdrawnAt(),
                account.getWithdrawReason(),
                Profile.of(account.getRole(), profileRow, skillRows),
                activity
        );
    }
}
