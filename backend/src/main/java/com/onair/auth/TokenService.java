package com.onair.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    static final String ISSUER = "onair";

    private final JwtEncoder encoder;
    private final RevokedTokenRepository revokedTokens;
    private final long ttlSeconds;

    public TokenService(
            JwtEncoder encoder,
            RevokedTokenRepository revokedTokens,
            @Value("${app.jwt.ttl-seconds:43200}") long ttlSeconds) {
        this.encoder = encoder;
        this.revokedTokens = revokedTokens;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .id(UUID.randomUUID().toString())
                        .subject(String.valueOf(user.getId()))
                        .claim("username", user.getUsername())
                        .claim("nickname", user.getNickname())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(ttlSeconds))
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public void revoke(Jwt jwt) {
        revokedTokens.save(new RevokedToken(jwt.getId(), jwt.getExpiresAt()));
    }

    public boolean isRevoked(String jti) {
        return jti != null && revokedTokens.existsById(jti);
    }

    @Scheduled(fixedDelay = 3_600_000)
    void purgeExpired() {
        revokedTokens.deleteExpired(Instant.now());
    }
}
