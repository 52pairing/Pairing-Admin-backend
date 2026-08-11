package com.pairing.admin.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 현재 로그인한 관리자를 주입한다.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ... me(@CurrentAdmin AdminPrincipal admin) { ... }
 * }</pre>
 *
 * <p>{@code SecurityContextHolder} 를 컨트롤러마다 직접 호출하면 테스트가 번거로워지고
 * 시큐리티 의존이 프레젠테이션 전체로 퍼진다. 주입 지점을 한 곳으로 모은다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentAdmin {
}
