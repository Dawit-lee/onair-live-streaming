package com.onair.donation;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.onair.auth.AuthUser;

@RestController
public class DonationController {
    private final DonationService service;
    private final TossPaymentsClient toss;

    public DonationController(DonationService service, TossPaymentsClient toss) {
        this.service = service;
        this.toss = toss;
    }

    public record OrderRequest(long amount, @Size(max = 200) String message) {}

    public record ConfirmRequest(@NotBlank String paymentKey, @NotBlank String orderId, long amount) {}

    @GetMapping("/api/donations/config")
    public Map<String, Object> config() {
        return Map.of("enabled", toss.enabled(), "clientKey", toss.clientKey());
    }

    @PostMapping("/api/broadcasts/{broadcastId}/donations/orders")
    public DonationService.OrderResponse order(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long broadcastId, @Valid @RequestBody OrderRequest req) {
        return service.createOrder(AuthUser.id(jwt), broadcastId, req.amount(), req.message());
    }

    @PostMapping("/api/donations/confirm")
    public DonationService.DonationItem confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConfirmRequest req) {
        return service.confirm(AuthUser.id(jwt), req.paymentKey(), req.orderId(), req.amount());
    }

    @GetMapping("/api/broadcasts/{broadcastId}/donations")
    public DonationService.Summary summary(@PathVariable long broadcastId) {
        return service.summary(broadcastId);
    }

    /** 토스 개발자센터에 등록하는 웹훅 URL. 항상 200을 돌려줘 재전송 폭주를 막고, 실제 검증은 결제 재조회로 한다. */
    @PostMapping("/api/donations/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> body) {
        Object data = body.get("data");
        if (data instanceof Map<?, ?> m && m.get("paymentKey") instanceof String key) {
            service.onWebhook(key);
        }
        return ResponseEntity.ok().build();
    }
}
