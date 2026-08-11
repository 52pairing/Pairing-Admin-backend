package com.pairing.admin.member.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원 정지(잠금) 요청")
public record MemberLockRequest(

        @Schema(description = "정지 사유. 회원 문의 시 근거가 되므로 필수로 받는다.",
                example = "약관 위반 신고 누적")
        @NotBlank(message = "정지 사유를 입력해 주세요.")
        @Size(max = 500, message = "정지 사유는 500자를 넘을 수 없습니다.")
        String reason
) {}
