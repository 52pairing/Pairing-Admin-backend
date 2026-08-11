package com.pairing.admin.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 관리자 서버 시큐리티 설정. <b>세션 로그인</b> 방식이다.
 *
 * <p>백엔드(Pairing-backend)는 JWT + STATELESS 인데 여기만 세션을 쓰는 이유:
 * <ul>
 *   <li>관리자는 수가 적고 브라우저에서만 접속한다. 무상태로 얻을 확장성이 필요 없다.</li>
 *   <li>세션은 서버가 로그인 상태를 들고 있어서 즉시 강제 로그아웃이 가능하다.
 *       JWT는 만료 전까지 유효해서 별도 블랙리스트가 필요하다.</li>
 *   <li>토큰을 브라우저 저장소에 두지 않아도 되므로 XSS로 인한 탈취 표면이 줄어든다.</li>
 * </ul>
 *
 * <p>대신 쿠키 기반이라 CSRF에 노출된다. 그래서 CSRF 보호를 기본으로 켜 둔다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 컨트롤러/서비스의 @PreAuthorize 활성화
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.security.csrf-enabled:true}")
    private boolean csrfEnabled;

    /** 로그인 없이 접근 가능한 경로. 새 공개 API가 생기면 반드시 여기에 명시적으로 추가한다. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/admin/auth/login",
            "/api/v1/admin/auth/csrf",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            // 시큐리티가 ERROR 디스패치까지 인가 검사를 하므로(6.x 기본값),
            // 열어두지 않으면 실제 예외가 401로 덮여 원인 파악이 어려워진다.
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션 로그인이므로 STATELESS 로 두면 안 된다.
                // IF_REQUIRED: 로그인 시점에만 세션을 만들고, 그 전까지는 만들지 않는다.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                .securityContext(context -> context
                        // 컨트롤러에서 직접 로그인 처리를 하므로 저장소를 명시적으로 지정한다.
                        // (AdminAuthService 가 같은 저장소에 saveContext 한다)
                        .securityContextRepository(securityContextRepository()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 이 서버는 관리자 전용이다. 나머지는 전부 ROLE_ADMIN 을 요구한다.
                        // permitAll 을 기본값으로 두면 @PreAuthorize 를 빠뜨린 새 API가 전체 공개되므로,
                        // "깜빡하면 닫히는" 쪽을 기본으로 삼는다.
                        .anyRequest().hasRole("ADMIN"))

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint) // 401 (로그인 안 됨 / 세션 만료)
                        .accessDeniedHandler(accessDeniedHandler))          // 403 (권한 없음 / CSRF)

                // 요청 캐시를 끈다. 기본 동작은 인증 실패한 요청을 세션에 저장해 두었다가
                // 로그인 후 그 화면으로 돌려보내는 것인데, 그러려고 <인증되지 않은 요청마다 세션을 만든다>.
                // SPA는 이동 경로를 프론트가 관리하므로 쓸모가 없고, 크롤러나 무작위 요청이
                // 세션을 계속 쌓게 만드는 부작용만 남는다.
                .requestCache(RequestCacheConfigurer::disable)

                // 폼 로그인·HTTP Basic 은 쓰지 않는다. 로그인은 AdminAuthController 가 JSON 으로 처리한다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 로그아웃도 컨트롤러에서 직접 처리한다. (응답을 ApiResponse 형태로 통일하기 위해)
                .logout(AbstractHttpConfigurer::disable);

        configureCsrf(http);

        return http.build();
    }

    /**
     * CSRF 설정.
     *
     * <p>XSRF-TOKEN 쿠키로 토큰을 내려주고 X-XSRF-TOKEN 헤더로 되받는다.
     * axios는 {@code withCredentials: true} 만 켜면 이 쿠키/헤더 조합을 자동으로 처리한다.
     *
     * <p>로그인 API는 예외로 둔다. 로그인 전에는 세션도 토큰도 없는 게 정상이고,
     * 실패해도 상태를 바꾸지 않으므로 CSRF의 보호 대상이 아니다.
     */
    private void configureCsrf(HttpSecurity http) throws Exception {
        if (!csrfEnabled) {
            // Swagger UI / curl 로 빠르게 확인할 때만 쓴다. 운영에서는 반드시 켠다.
            http.csrf(AbstractHttpConfigurer::disable);
            return;
        }

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        // 기본값(XorCsrfTokenRequestAttributeHandler)은 매 응답마다 값이 달라지는 BREACH 방어용이라
        // 쿠키에서 값을 그대로 읽어 헤더에 넣는 SPA 방식과 맞지 않는다. 평문 핸들러로 바꾼다.
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http.csrf(csrf -> csrf
                        // httpOnly=false 여야 자바스크립트가 쿠키를 읽어 헤더에 실을 수 있다.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers("/api/v1/admin/auth/login"))
                // 토큰을 실제로 꺼내야 쿠키가 발급된다. (CsrfCookieFilter 주석 참고)
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);
    }

    /**
     * 세션에 SecurityContext 를 저장하는 저장소.
     *
     * <p>스프링 시큐리티 6부터 인증 정보가 자동으로 세션에 저장되지 않는다.
     * 직접 로그인 처리를 하는 코드가 이 빈으로 {@code saveContext} 를 호출해야 로그인이 유지된다.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * 컨트롤러에서 아이디/비밀번호를 검증할 때 쓴다.
     *
     * <p>{@code UserDetailsService} 빈과 {@code PasswordEncoder} 빈이 있으면
     * 스프링 부트가 DaoAuthenticationProvider 를 자동으로 엮어 준다.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 세션 쿠키를 주고받으므로 와일드카드(*)를 쓸 수 없다. 관리자 프론트 도메인을 정확히 명시한다.
        configuration.setAllowedOriginPatterns(
                Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        // 쿠키(세션·CSRF) 통신을 위해 반드시 true
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
