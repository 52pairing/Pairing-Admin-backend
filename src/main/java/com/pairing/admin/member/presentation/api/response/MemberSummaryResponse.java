package com.pairing.admin.member.presentation.api.response;

import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.MemberStatusFilter;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.domain.SignupMethod;
import com.pairing.admin.member.domain.SignupType;
import com.pairing.admin.member.infrastructure.persistence.MemberAdminQueryRepository.ListRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "회원 목록 항목")
public record MemberSummaryResponse(

        @Schema(description = "계정 ID. 화면의 회원번호로 그대로 쓴다.", example = "12")
        Long accountId,

        @Schema(description = "이름(클라이언트는 담당자명)", example = "홍길동")
        String name,

        @Schema(description = "기업명. 클라이언트만 값이 있다.", example = "삼성전자")
        String companyName,

        @Schema(description = "이메일", example = "hong@example.com")
        String email,

        @Schema(description = "휴대폰", example = "01012345678")
        String phone,

        @Schema(description = "역할", example = "FREELANCER")
        Role role,

        @Schema(description = "역할 표시명", example = "프리랜서")
        String roleLabel,

        @Schema(description = "화면에 표시할 상태. 정지가 계정 상태보다 우선한다.", example = "ACTIVE")
        MemberStatusFilter status,

        @Schema(description = "상태 표시명", example = "정상")
        String statusLabel,

        @Schema(description = "정지 여부", example = "false")
        boolean suspended,

        @Schema(description = "가입방식", example = "KAKAO")
        SignupMethod signupMethod,

        @Schema(description = "가입방식 표시명", example = "카카오")
        String signupMethodLabel,

        @Schema(description = "가입일")
        LocalDateTime createdAt,

        @Schema(description = "최근 로그인")
        LocalDateTime lastLoginAt,

        @Schema(description = "종료·취소되지 않은 프로젝트 건수", example = "2")
        long activeProjectCount
) {

    public static MemberSummaryResponse from(ListRow row) {
        boolean suspended = Boolean.TRUE.equals(row.getSuspended());

        Role role = Role.valueOf(row.getRole());
        MemberStatusFilter status =
                MemberStatusFilter.of(AccountStatus.valueOf(row.getStatus()), suspended);
        SignupMethod signupMethod =
                SignupMethod.of(SignupType.valueOf(row.getSignupType()), row.getProvider());

        return new MemberSummaryResponse(
                row.getAccountId(),
                row.getName(),
                row.getCompanyName(),
                row.getEmail(),
                row.getPhone(),
                role,
                role.getLabel(),
                status,
                status.getLabel(),
                suspended,
                signupMethod,
                signupMethod.getLabel(),
                row.getCreatedAt(),
                row.getLastLoginAt(),
                // 역할이 ADMIN 이면 서브쿼리가 아무것도 못 세서 null 이 올 수 있다.
                row.getActiveProjectCount() == null ? 0L : row.getActiveProjectCount()
        );
    }
}
