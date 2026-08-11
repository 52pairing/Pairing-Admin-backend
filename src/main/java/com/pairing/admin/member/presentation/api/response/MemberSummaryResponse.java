package com.pairing.admin.member.presentation.api.response;

import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.domain.SignupType;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "회원 목록 항목")
public record MemberSummaryResponse(

        @Schema(description = "계정 ID", example = "12")
        Long accountId,

        @Schema(description = "이름", example = "홍길동")
        String name,

        @Schema(description = "이메일", example = "hong@example.com")
        String email,

        @Schema(description = "휴대폰", example = "010-1234-5678")
        String phone,

        @Schema(description = "역할", example = "FREELANCER")
        Role role,

        @Schema(description = "역할 표시명", example = "프리랜서")
        String roleLabel,

        @Schema(description = "상태", example = "ACTIVE")
        AccountStatus status,

        @Schema(description = "상태 표시명", example = "정상")
        String statusLabel,

        @Schema(description = "가입 경로", example = "EMAIL")
        SignupType signupType,

        @Schema(description = "가입일")
        LocalDateTime createdAt,

        @Schema(description = "최근 로그인")
        LocalDateTime lastLoginAt
) {

    public static MemberSummaryResponse from(AccountJpaEntity account) {
        return new MemberSummaryResponse(
                account.getId(),
                account.getName(),
                account.getEmail(),
                account.getPhone(),
                account.getRole(),
                account.getRole().getLabel(),
                account.getStatus(),
                account.getStatus().getLabel(),
                account.getSignupType(),
                account.getCreatedAt(),
                account.getLastLoginAt()
        );
    }
}
