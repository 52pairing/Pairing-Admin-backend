package com.pairing.admin.member.infrastructure.redis;

/**
 * 백엔드(Pairing-backend)가 쓰는 Redis 키.
 *
 * <p><b>이것은 백엔드와의 계약이다.</b> 원본은 백엔드의
 * {@code com.pairing.global.util.RedisKeys} 이며, 값이 어긋나면 정지를 눌러도 아무 일도
 * 일어나지 않는다. DB 스키마와 달리 Redis 에는 이 불일치를 잡아 줄 제약이 없어서,
 * 컴파일 에러도 기동 실패도 없이 조용히 동작을 멈춘다.
 * 백엔드에서 키 이름을 바꾸면 <b>여기도 반드시 같이 고친다.</b>
 *
 * <p>키 직렬화는 맞아야 한다. 백엔드가 {@code StringRedisSerializer} 를 쓰므로,
 * 이 서버도 같은 직렬화를 쓰는 {@code StringRedisTemplate} 을 그대로 사용한다.
 * ({@link BackendSuspensionGateway} 참고)
 */
public final class BackendSessionKeys {

    /**
     * 회원 정지 마커. 백엔드 {@code AccountSuspensionPort} 가 <b>존재 여부만</b> 본다.
     *
     * <p>백엔드는 {@code hasKey} 로만 확인하므로 값은 무엇이든 상관없다. 다만 redis-cli 로
     * 들여다볼 때 언제 누가 걸었는지 알 수 있도록 사람이 읽을 수 있는 값을 넣는다.
     *
     * <p>TTL 을 걸지 않는다. 정지는 관리자가 풀 때까지 유지돼야 하고, 만료 시간을 두면
     * 아무도 해제하지 않았는데 정지가 저절로 풀린다.
     */
    public static final String SUSPEND_PREFIX = "SUSPEND:";

    /** 리프레시 토큰. 지우면 액세스 토큰 재발급이 막힌다. */
    public static final String REFRESH_TOKEN_PREFIX = "RT:";

    /**
     * 현재 세션 ID. 백엔드 JWT 필터가 매 요청 이 값을 읽어 토큰의 sid 와 대조한다.
     * 지우면 다음 요청에서 곧바로 401 이 된다. 즉시 차단의 핵심이 이 키다.
     */
    public static final String SESSION_PREFIX = "SESSION:";

    private BackendSessionKeys() {
    }
}
