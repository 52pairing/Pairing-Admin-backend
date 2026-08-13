package com.pairing.admin;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

/**
 * 페어링 관리자 서버.
 *
 * <p>백엔드(Pairing-backend)와 <b>같은 RDS·같은 S3 버킷</b>을 바라보는 별도 애플리케이션이다.
 * 서버만 분리했을 뿐 데이터는 하나이므로, DB/S3 관련 환경변수는 백엔드와 동일한 값을 주입해야 한다.
 *
 * <p>인증 방식만 다르다. 백엔드는 JWT(무상태)를 쓰고, 관리자 서버는 세션(JSESSIONID 계열 쿠키)을 쓴다.
 * 관리자는 사용자 수가 적고 브라우저에서만 접근하므로, 서버가 로그인 상태를 들고 있는 편이
 * 즉시 강제 로그아웃이 가능해 관리 화면에 더 맞다.
 */
@SpringBootApplication
@EnableAsync
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

    /**
     * JVM 기본 타임존을 KST로 고정한다.
     *
     * <p>EC2/컨테이너는 기본이 UTC다. 관리자 화면은 "오늘 가입자" 같은 날짜 경계 집계를 자주 보여주는데,
     * 타임존이 어긋나면 백엔드와 다른 숫자가 나온다.
     */
    @PostConstruct
    public void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }
}
