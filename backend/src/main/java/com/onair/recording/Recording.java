package com.onair.recording;

import java.time.Instant;

import jakarta.persistence.*;

import com.onair.auth.User;

@Entity
@Table(name = "recordings")
public class Recording {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long broadcastId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    private String title;
    private String filePath;
    private String contentType;
    private long sizeBytes;
    private int chunkCount;

    @Enumerated(EnumType.STRING)
    private RecordingStatus status;

    private Instant startedAt;
    private Instant endedAt;

    protected Recording() {}

    public Recording(long broadcastId, User owner, String title, String contentType) {
        this.broadcastId = broadcastId;
        this.owner = owner;
        this.title = title;
        this.contentType = contentType;
        this.filePath = "";
        this.status = RecordingStatus.RECORDING;
        this.startedAt = Instant.now();
    }

    void assignFile(String filePath) { this.filePath = filePath; }

    void appended(long bytes) {
        this.sizeBytes += bytes;
        this.chunkCount++;
    }

    void finish() {
        if (status == RecordingStatus.READY) return;
        this.status = RecordingStatus.READY;
        this.endedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getBroadcastId() { return broadcastId; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getFilePath() { return filePath; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getChunkCount() { return chunkCount; }
    public RecordingStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
}
