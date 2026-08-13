package com.pairing.admin.member.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 백엔드와 공유하는 Redis 에 정지를 반영한다.
 *
 * <p>백엔드는 회원 정지를 <b>DB 가 아니라 Redis 에서</b> 읽는다.
 * ({@code AccountSuspensionPort} — "정지 처리(등록/해제)는 관리자 도메인이 담당하고,
 * 여기서는 로그인 차단을 위해 조회만 한다") 그래서 이 클래스가 정지의 실제 효력을 만든다.
 * {@code account.suspended_at} 컬럼은 목록 필터와 집계를 위한 사본일 뿐이다.
 *
 * <p>정지 한 번에 키를 두 종류 건드린다. 역할이 다르다.
 * <ul>
 *   <li>{@code SUSPEND:{id}} 를 <b>만든다</b> → 앞으로의 로그인이 막힌다</li>
 *   <li>{@code RT:{id}} · {@code SESSION:{id}} 를 <b>지운다</b> → 이미 로그인해 둔 세션이 즉시 끊긴다</li>
 * </ul>
 * 두 번째를 빠뜨리면 정지해도 액세스 토큰 수명(기본 30분)만큼 서비스를 계속 쓸 수 있다.
 *
 * <p><b>실패하면 예외를 그대로 올린다.</b> 호출부의 트랜잭션이 롤백되어 DB 의 정지 표시도
 * 함께 취소되므로, "관리자 화면에는 정지라고 뜨는데 회원은 멀쩡히 쓰고 있는" 상태가 남지 않는다.
 * 정지 버튼이 실패로 끝나면 관리자가 다시 누를 수 있지만, 성공했다고 잘못 알면 아무도 눈치채지 못한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendSuspensionGateway {

    /**
     * {@code StringRedisTemplate} 을 쓴다. 키를 {@code StringRedisSerializer} 로 직렬화하는데,
     * 백엔드의 {@code RedisConfig} 와 같은 방식이라 별도 설정 없이 같은 키를 가리킨다.
     */
    private final StringRedisTemplate redisTemplate;

    /** 정지 마커를 만들고, 살아 있는 세션을 끊는다. */
    public void suspend(Long accountId, Long actorAdminId, LocalDateTime at) {
        // 값은 백엔드가 읽지 않는다(hasKey 로 존재만 확인). redis-cli 로 볼 때의 단서로만 쓴다.
        redisTemplate.opsForValue()
                .set(BackendSessionKeys.SUSPEND_PREFIX + accountId, "admin=" + actorAdminId + ";at=" + at);

        Long deleted = redisTemplate.delete(sessionKeys(accountId));

        log.info("[백엔드 정지 반영] accountId={}, 끊긴 세션 키={}", accountId, deleted);
    }

    /**
     * 정지 마커를 지운다. 다음 로그인부터 통과한다.
     *
     * <p>세션 키는 건드릴 것이 없다. 정지할 때 이미 지웠고, 정지 중에는 로그인 자체가 막혀
     * 새로 생기지도 않는다.
     */
    public void release(Long accountId) {
        Boolean deleted = redisTemplate.delete(BackendSessionKeys.SUSPEND_PREFIX + accountId);

        // 키가 없어도 성공으로 둔다. 결과가 "정지가 아닌 상태" 로 같기 때문이다.
        // Redis 가 비워졌거나 사람이 직접 지운 뒤 해제를 누르는 경우가 여기 해당한다.
        log.info("[백엔드 정지 해제] accountId={}, 마커 삭제={}", accountId, deleted);
    }

    private List<String> sessionKeys(Long accountId) {
        return List.of(
                BackendSessionKeys.REFRESH_TOKEN_PREFIX + accountId,
                BackendSessionKeys.SESSION_PREFIX + accountId
        );
    }
}
