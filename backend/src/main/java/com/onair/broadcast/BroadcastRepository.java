package com.onair.broadcast;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {
    @EntityGraph(attributePaths = "owner")
    List<Broadcast> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "owner")
    List<Broadcast> findByStatusOrderByCreatedAtDesc(BroadcastStatus status);

    Optional<Broadcast> findByRoomName(String roomName);
}
