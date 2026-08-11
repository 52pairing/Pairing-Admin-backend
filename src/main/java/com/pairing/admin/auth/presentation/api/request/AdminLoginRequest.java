package com.pairing.admin.auth.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 로그인 요청")
public record AdminLoginRequest(

        // 이메일이 아니라 아이디다. admin_user 테이블은 회원과 분리되어 있어
        // 이메일 형식을 강제할 이유가 없다.
        @Schema(description = "관리자 아이디", example = "admin")
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(max = 50, message = "아이디는 50자를 넘을 수 없습니다.")
        String username,

        @Schema(description = "비밀번호", example = "Admin!2345")
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {}
