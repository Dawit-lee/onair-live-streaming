package com.onair.comment;

import java.time.Instant;

import jakarta.persistence.*;

import com.onair.auth.User;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long broadcastId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    protected Comment() {}

    public Comment(long broadcastId, User author, String content) {
        this.broadcastId = broadcastId;
        this.author = author;
        this.content = content;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public void edit(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getBroadcastId() { return broadcastId; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
