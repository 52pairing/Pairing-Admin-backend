package com.pairing.admin.notification.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 서버(pairing-backend)에 알림 생성을 맡긴다.
 *
 * <p><b>왜 DB 에 직접 넣지 않는가</b> — 행만 넣으면 실시간 push 가 안 나간다. WebSocket 세션은
 * 사용자 서버가 들고 있어서 이 프로세스는 그 세션에 아무것도 보낼 수 없다. 그래서 1:1 문의 답변
 * 알림만 사용자가 새로고침해야 보이는 상태였다(2026-08-15 QA 확인).
 *
 * <p>사용자 서버가 저장과 push 를 함께 처리하므로 이쪽은 push 를 신경 쓸 필요가 없다.
 *
 * <p>{@code PythonEmbeddingClient} 와 같은 패턴이고, 같은 내부 호출 키를 쓴다.
 */
@Slf4j
@Component
public class PairingBackendNotificationClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public PairingBackendNotificationClient(
            @Value("${pairing-backend.base-url:http://localhost:8080}") String baseUrl,
            @Value("${pairing-backend.internal-api-key:}") String internalApiKey) {
        this.internalApiKey = internalApiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        // 알림 저장 + push 는 짧게 끝난다. 길게 잡으면 관리자가 답변 버튼을 누르고
        // 그만큼 기다리게 된다 — 알림은 부수 작업이라 그럴 이유가 없다.
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    /** 실패는 호출부가 삼킨다. 여기서는 그대로 던진다. */
    public void create(Long ownerAccountId, String type, String title, String content, String linkUrl) {
        // content/linkUrl 이 null 일 수 있어 Map.of 를 쓸 수 없다(NPE).
        Map<String, Object> body = new HashMap<>();
        body.put("ownerAccountId", ownerAccountId);
        body.put("type", type);
        body.put("title", title);
        body.put("content", content);
        body.put("linkUrl", linkUrl);

        restClient.post()
                .uri("/api/v1/internal/notifications")
                .headers(headers -> {
                    headers.add(INTERNAL_API_KEY_HEADER, internalApiKey);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
