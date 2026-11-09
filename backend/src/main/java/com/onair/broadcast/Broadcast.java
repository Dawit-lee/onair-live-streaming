package com.onair.broadcast;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

import com.onair.auth.User;
import com.onair.common.ApiException;

@Entity
@Table(name = "broadcasts")
public class Broadcast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    private String title;
    private String description;
    private String category;

    @Enumerated(EnumType.STRING)
    private BroadcastStatus status;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    private String roomName;
    private Instant startedAt;
    private Instant endedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected Broadcast() {}

    public Broadcast(User owner, String title, String description, String category) {
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.category = category;
        this.status = BroadcastStatus.READY;
        this.roomName = newRoomName();
        this.createdAt = this.updatedAt = Instant.now();
    }

    public boolean isOwnedBy(long userId) {
        return owner.getId() == userId;
    }

    public void update(String title, String description, String category) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.updatedAt = Instant.now();
    }

    /** 송출 시작. 시작마다 새 방 이름을 써서 이전 세션의 늦은 웹훅이 새 방송을 끝내지 않게 한다. */
    public void start(SourceType sourceType) {
        if (status == BroadcastStatus.LIVE) {
            throw ApiException.conflict("이미 방송 중입니다.");
        }
        this.status = BroadcastStatus.LIVE;
        this.sourceType = sourceType;
        this.roomName = newRoomName();
        this.startedAt = Instant.now();
        this.endedAt = null;
        this.updatedAt = this.startedAt;
    }

    /** 이미 종료된 방송이면 false (중복 종료 요청은 무시). */
    public boolean end() {
        if (status != BroadcastStatus.LIVE) return false;
        this.status = BroadcastStatus.ENDED;
        this.endedAt = Instant.now();
        this.updatedAt = this.endedAt;
        return true;
    }

    private String newRoomName() {
        return "bc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BroadcastStatus getStatus() { return status; }
    public SourceType getSourceType() { return sourceType; }
    public String getRoomName() { return roomName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
