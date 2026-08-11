package com.pairing.admin.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI adminOpenAPI() {
        // 세션 쿠키 인증이라 Swagger UI에서 별도 토큰 입력이 필요 없다.
        // /auth/login 을 한 번 호출하면 브라우저가 쿠키를 갖게 되고, 이후 요청에 자동으로 실린다.
        SecurityScheme sessionCookie = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("ADMIN_SESSION");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("sessionCookie", sessionCookie))
                .info(new Info()
                        .title("페어링 관리자 API")
                        .version("v1")
                        .description("""
                                관리자 전용 서버입니다. 백엔드(Pairing-backend)와 같은 DB·S3를 공유하며,
                                인증만 세션 방식을 사용합니다.

                                [Swagger UI 에서 테스트하는 법]
                                1. POST /api/v1/admin/auth/login 을 실행한다. (쿠키가 자동으로 저장된다)
                                2. 이후 API를 그대로 호출한다.

                                CSRF가 켜져 있으면(기본값) 로그인 외의 POST/PATCH/DELETE 는
                                X-XSRF-TOKEN 헤더가 필요하다. Swagger 로 편하게 테스트하려면
                                CSRF_ENABLED=false 로 띄운다. (운영에서는 반드시 true)
                                """));
    }
}
