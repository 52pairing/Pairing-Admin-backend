package com.pairing.admin.global.security;

import com.pairing.admin.global.exception.BusinessException;
import com.pairing.admin.global.exception.GlobalErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentAdminArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAdmin.class)
                && AdminPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 시큐리티 설정상 여기까지 오면 인증된 상태여야 한다.
        // 그래도 방어한다. permitAll 경로에 실수로 @CurrentAdmin 을 붙이면 NPE 대신 401이 나가게 한다.
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        return principal;
    }
}
