package com.pairing.admin.auth.presentation.api.response;

import com.pairing.admin.auth.infrastructure.persistence.AdminUserJpaEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "로그인한 관리자 정보")
public record AdminMeResponse(

        @Schema(description = "관리자 ID", example = "1")
        Long adminId,

        @Schema(description = "로그인 아이디", example = "admin")
        String username,

        @Schema(description = "직전 로그인 시각")
        LocalDateTime lastLoginAt
) {

    public static AdminMeResponse from(AdminUserJpaEntity admin) {
        return new AdminMeResponse(
                admin.getId(),
                admin.getUsername(),
                admin.getLastLoginAt()
        );
    }
}
