package com.pairing.admin.auth.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSRF 토큰")
public record CsrfTokenResponse(

        @Schema(description = "토큰을 실어 보낼 헤더 이름", example = "X-XSRF-TOKEN")
        String headerName,

        @Schema(description = "토큰 값. 쿠키(XSRF-TOKEN)로도 같은 값이 내려간다.")
        String token,

        @Schema(description = "CSRF 보호 활성화 여부. false 면 토큰 없이 호출해도 된다.", example = "true")
        boolean enabled
) {

    public static CsrfTokenResponse disabled() {
        return new CsrfTokenResponse(null, null, false);
    }
}
