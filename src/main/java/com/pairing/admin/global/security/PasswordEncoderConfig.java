package com.pairing.admin.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더.
 *
 * <p>백엔드(Pairing-backend)와 <b>반드시 같은 방식</b>이어야 한다.
 * account.password_hash 는 두 서버가 공유하는 컬럼이라, 인코더가 다르면
 * 한쪽에서 바꾼 비밀번호로 다른 쪽 로그인이 안 된다. (백엔드도 BCrypt 기본 설정)
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
