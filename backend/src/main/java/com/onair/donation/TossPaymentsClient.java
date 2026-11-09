package com.onair.donation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.onair.common.ApiException;

/** 토스페이먼츠 결제 승인/조회 API. 테스트 키(test_sk_...)를 쓰면 실제 결제가 일어나지 않는다. */
@Component
public class TossPaymentsClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final String clientKey;
    private final boolean enabled;

    public TossPaymentsClient(
            @Value("${app.toss.client-key:}") String clientKey,
            @Value("${app.toss.secret-key:}") String secretKey) {
        this.clientKey = clientKey;
        this.enabled = !clientKey.isBlank() && !secretKey.isBlank();
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.rest = RestClient.builder().baseUrl("https://api.tosspayments.com").defaultHeader("Authorization", "Basic " + basic).build();
    }

    public boolean enabled() {
        return enabled;
    }

    public String clientKey() {
        return clientKey;
    }

    public Map<String, Object> confirm(String paymentKey, String orderId, long amount) {
        try {
            return rest.post()
                    .uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .body(MAP);
        } catch (RestClientResponseException e) {
            throw ApiException.badRequest("결제 승인 실패: " + e.getResponseBodyAsString());
        }
    }

    public Map<String, Object> get(String paymentKey) {
        try {
            return rest.get().uri("/v1/payments/{key}", paymentKey).retrieve().body(MAP);
        } catch (RestClientResponseException e) {
            throw ApiException.badRequest("결제 조회 실패");
        }
    }
}
