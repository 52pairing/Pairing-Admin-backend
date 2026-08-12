package com.pairing.admin.support.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "문의 답변 등록 요청")
public record InquiryAnswerRequest(

        @Schema(description = "답변 내용", example = "안녕하세요. 착수금 수수료 결제는 프로젝트 등록 후 AI 검수 결과 확인 후 진행할 수 있습니다.")
        @NotBlank(message = "답변 내용을 입력해 주세요.")
        @Size(max = 2000, message = "답변은 2,000자 이하로 입력해 주세요.")
        String answer
) {
}
