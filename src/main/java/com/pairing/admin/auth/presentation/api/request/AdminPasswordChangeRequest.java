package com.pairing.admin.auth.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "관리자 비밀번호 변경 요청")
public record AdminPasswordChangeRequest(

        @Schema(description = "현재 비밀번호")
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @Schema(description = "새 비밀번호 (영문·숫자·특수문자 포함 8~30자)")
        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,30}$",
                message = "비밀번호는 영문·숫자·특수문자를 모두 포함한 8~30자여야 합니다."
        )
        String newPassword
) {}
