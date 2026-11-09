package com.onair.auth;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String passwordHash;
    private String nickname;
    private Instant createdAt;

    protected User() {}

    public User(String username, String passwordHash, String nickname) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
}
