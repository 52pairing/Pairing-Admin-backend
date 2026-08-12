package com.pairing.admin.support.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문의 첨부파일")
public record InquiryAttachmentResponse(

        @Schema(description = "파일 ID", example = "42")
        Long fileId,

        @Schema(description = "업로드 당시 원본 파일명", example = "오류화면.png")
        String originalName,

        @Schema(description = "다운로드 URL. object key 앞에 CDN 루트가 붙은 값이다.",
                example = "https://pairing-bucket.s3.ap-northeast-2.amazonaws.com/dev/inquiry_attachment/3f1c....png")
        String url
) {
}
