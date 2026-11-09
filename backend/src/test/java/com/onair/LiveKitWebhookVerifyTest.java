package com.onair;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.onair.livekit.LiveKitProperties;
import com.onair.livekit.LiveKitService;

class LiveKitWebhookVerifyTest {
    static final String SECRET = "test-livekit-secret-that-is-32-bytes-or-more";
    final LiveKitService svc = new LiveKitService(new LiveKitProperties("ws://x", "http://x", "key", SECRET, 60));

    @Test
    void acceptsSignedBody_rejectsTamperedOrWrongKey() throws Exception {
        String body = "{\"event\":\"participant_left\"}";
        assertThat(svc.verifyWebhook(sign(body, SECRET, "key"), body)).isTrue();
        assertThat(svc.verifyWebhook(sign(body, SECRET, "key"), body + " ")).isFalse();
        assertThat(svc.verifyWebhook(sign(body, "another-secret-that-is-also-32-bytes-long", "key"), body)).isFalse();
        assertThat(svc.verifyWebhook(sign(body, SECRET, "other"), body)).isFalse();
        assertThat(svc.verifyWebhook(null, body)).isFalse();
    }

    private static String sign(String body, String secret, String key) throws Exception {
        String hash = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        var claims = new JWTClaimsSet.Builder().issuer(key).claim("sha256", hash)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000)).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
