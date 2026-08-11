package com.pairing.admin.global.config;

import com.pairing.admin.global.security.CurrentAdminArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * MVC 설정.
 *
 * <p>CORS는 여기서 다루지 않는다. {@code AdminSecurityConfig} 가 전담한다.
 * 두 곳에서 설정하면 어느 쪽이 적용됐는지 추적하기 어려워진다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentAdminArgumentResolver currentAdminArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentAdminArgumentResolver);
    }
}
