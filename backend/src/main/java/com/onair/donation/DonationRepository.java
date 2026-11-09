package com.onair.donation;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    /** 동시에 들어온 승인 요청/웹훅을 직렬화하기 위한 행 잠금. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.orderId = :orderId")
    Optional<Donation> lockByOrderId(String orderId);

    @EntityGraph(attributePaths = "donor")
    List<Donation> findTop100ByBroadcastIdAndStatusOrderByApprovedAtDesc(long broadcastId, DonationStatus status);

    @Query("select coalesce(sum(d.amount), 0) from Donation d where d.broadcastId = :broadcastId and d.status = com.onair.donation.DonationStatus.DONE")
    long sumDone(long broadcastId);

    @Query("select count(d) from Donation d where d.broadcastId = :broadcastId and d.status = com.onair.donation.DonationStatus.DONE")
    long countDone(long broadcastId);
}
