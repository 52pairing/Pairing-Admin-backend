package com.pairing.admin.matching.infrastructure.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PythonEmbeddingClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public PythonEmbeddingClient(@Value("${ai.pairing-python.base-url:http://localhost:8000}")
                                 String baseUrl,
                                 @Value("${ai.pairing-python.internal-api-key:}")
                                 String internalApiKey) {
        this.internalApiKey = internalApiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    public void upsertFreelancer(Long freelancerId, String text) {
        restClient.put()
                .uri("/api/v1/embeddings/freelancers")
                .headers(headers -> {
                    headers.add(INTERNAL_API_KEY_HEADER, internalApiKey);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .body(Map.of("freelancer_id", freelancerId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }

    public void upsertPosition(Long positionId, String text) {
        restClient.put()
                .uri("/api/v1/embeddings/positions")
                .headers(headers -> {
                    headers.add(INTERNAL_API_KEY_HEADER, internalApiKey);
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .body(Map.of("position_id", positionId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
