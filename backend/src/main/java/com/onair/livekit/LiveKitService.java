package com.onair.livekit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * LiveKit 접속 토큰 발급, 방 강제 종료, 웹훅 서명 검증을 담당한다.
 *
 * <p>토큰 발급은 Meetbowl의 NimbusLiveKitTokenIssuer를 가져왔고, 방송용으로 송출 권한(canPublish)을 소유자에게만
 * 주도록 바꿨다. API secret은 서버에만 있다.
 */
@Service
public class LiveKitService {
    private static final Logger log = LoggerFactory.getLogger(LiveKitService.class);

    private final LiveKitProperties props;
    private final RestClient rest;

    public LiveKitService(LiveKitProperties props) {
        this.props = props;
        this.rest = RestClient.builder().baseUrl(props.apiUrl()).build();
    }

    public String publicUrl() {
        return props.publicUrl();
    }

    /** 송출자 토큰: 발행 + 구독 + 데이터 채널. */
    public String publisherToken(String room, String identity, String name) {
        return sign(
                identity,
                name,
                Map.of(
                        "room", room,
                        "roomJoin", true,
                        "canPublish", true,
                        "canSubscribe", true,
                        "canPublishData", true),
                props.tokenTtlSeconds());
    }

    /** 시청자 토큰: 구독만 가능. 시청자가 영상/음성을 올리는 것은 LiveKit 서버가 거부한다. */
    public String viewerToken(String room, String identity) {
        return sign(
                identity,
                "viewer",
                Map.of(
                        "room", room,
                        "roomJoin", true,
                        "canPublish", false,
                        "canSubscribe", true,
                        "canPublishData", false,
                        "hidden", true),
                props.tokenTtlSeconds());
    }

    /** 방을 삭제하면 송출자와 시청자 연결이 모두 끊긴다. 방송 종료 시 호출. */
    public void deleteRoom(String room) {
        try {
            rest.post()
                    .uri("/twirp/livekit.RoomService/DeleteRoom")
                    .header("Authorization", "Bearer " + adminToken(room))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("room", room))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 방이 이미 없으면(송출자가 먼저 나감) 404가 온다. 종료 처리 자체는 계속 진행한다.
            log.warn("LiveKit DeleteRoom failed for {}: {}", room, e.getMessage());
        }
    }

    /**
     * LiveKit 웹훅 검증: Authorization 헤더는 API secret으로 서명된 JWT이고, sha256 클레임은 본문의 SHA-256(base64)이다.
     */
    public boolean verifyWebhook(String authorization, String body) {
        if (authorization == null || body == null) return false;
        try {
            String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(props.apiSecret().getBytes(StandardCharsets.UTF_8)))) return false;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!props.apiKey().equals(claims.getIssuer())) return false;
            if (claims.getExpirationTime() != null && claims.getExpirationTime().before(new Date())) return false;
            String expected =
                    Base64.getEncoder()
                            .encodeToString(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(body.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    String.valueOf(claims.getStringClaim("sha256")).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private String adminToken(String room) {
        return sign(null, null, Map.of("room", room, "roomAdmin", true, "roomCreate", true), 60);
    }

    private String sign(String identity, String name, Map<String, Object> video, long ttlSeconds) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder claims =
                    new JWTClaimsSet.Builder()
                            .issuer(props.apiKey())
                            // 클라이언트와 서버 시계 차이로 즉시 거부되지 않도록 약간 과거 시각을 허용한다.
                            .notBeforeTime(Date.from(now.minusSeconds(30)))
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                            .claim("video", video);
            if (identity != null) claims.subject(identity);
            if (name != null) claims.claim("name", name);
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(props.apiSecret().getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("LiveKit token generation failed", e);
        }
    }
}
