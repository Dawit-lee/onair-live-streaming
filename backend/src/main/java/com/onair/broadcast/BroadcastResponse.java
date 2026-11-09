package com.onair.broadcast;

import java.time.Instant;

public record BroadcastResponse(
        long id,
        String title,
        String description,
        String category,
        BroadcastStatus status,
        SourceType sourceType,
        long ownerId,
        String ownerNickname,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt,
        Instant updatedAt) {
    static BroadcastResponse of(Broadcast b) {
        return new BroadcastResponse(
                b.getId(),
                b.getTitle(),
                b.getDescription(),
                b.getCategory(),
                b.getStatus(),
                b.getSourceType(),
                b.getOwner().getId(),
                b.getOwner().getNickname(),
                b.getStartedAt(),
                b.getEndedAt(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }
}
