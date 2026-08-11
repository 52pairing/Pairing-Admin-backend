package com.pairing.admin.member.presentation.api.response;

import com.pairing.admin.member.domain.AccountStatus;
import com.pairing.admin.member.domain.Role;
import com.pairing.admin.member.domain.SignupType;
import com.pairing.admin.member.infrastructure.persistence.AccountJpaEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "회원 상세")
public record MemberDetailResponse(

        Long accountId,
        String name,
        String email,
        String phone,
        Role role,
        String roleLabel,
        AccountStatus status,
        String statusLabel,
        SignupType signupType,

        @Schema(description = "이메일 인증 완료 여부")
        boolean emailVerified,

        @Schema(description = "비밀번호 연속 실패 횟수")
        int loginFailCount,

        @Schema(description = "잠금 시각")
        LocalDateTime lockedAt,

        @Schema(description = "정지/잠금 사유")
        String suspendReason,

        @Schema(description = "최근 로그인")
        LocalDateTime lastLoginAt,

        @Schema(description = "가입일")
        LocalDateTime createdAt,

        @Schema(description = "정보 수정일")
        LocalDateTime updatedAt
) {

    public static MemberDetailResponse from(AccountJpaEntity account) {
        return new MemberDetailResponse(
                account.getId(),
                account.getName(),
                account.getEmail(),
                account.getPhone(),
                account.getRole(),
                account.getRole().getLabel(),
                account.getStatus(),
                account.getStatus().getLabel(),
                account.getSignupType(),
                account.isEmailVerified(),
                account.getLoginFailCount(),
                account.getLockedAt(),
                account.getSuspendReason(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
