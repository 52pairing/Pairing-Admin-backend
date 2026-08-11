package com.pairing.admin.file.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "업로드 결과")
public record UploadedFileResponse(

        @Schema(description = "저장된 object key. DB에는 이 값을 저장한다.",
                example = "dev/admin/3f1c2a54-....png")
        String key,

        @Schema(description = "조회용 절대 URL. key 앞에 CDN 루트를 붙인 값이다.",
                example = "https://pairing-bucket.s3.ap-northeast-2.amazonaws.com/dev/admin/3f1c2a54-....png")
        String url
) {}
