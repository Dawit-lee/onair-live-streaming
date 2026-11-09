package com.onair.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {
    @Id private String jti;
    private Instant expiresAt;

    protected RevokedToken() {}

    public RevokedToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }
}
