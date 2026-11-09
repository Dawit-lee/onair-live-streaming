package com.onair.recording;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RecordingRepository extends JpaRepository<Recording, Long> {
    @EntityGraph(attributePaths = "owner")
    List<Recording> findByStatusOrderByStartedAtDesc(RecordingStatus status);

    List<Recording> findByBroadcastId(long broadcastId);

    List<Recording> findByBroadcastIdAndStatus(long broadcastId, RecordingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recording r where r.id = :id")
    Optional<Recording> lockById(long id);
}
