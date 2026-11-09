package com.onair.donation;

import java.time.Instant;

import jakarta.persistence.*;

import com.onair.auth.User;

@Entity
@Table(name = "donations")
public class Donation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;
    private String paymentKey;
    private Long broadcastId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id")
    private User donor;

    private Long streamerId;
    private long amount;
    private String message;

    @Enumerated(EnumType.STRING)
    private DonationStatus status;

    private Instant createdAt;
    private Instant approvedAt;

    protected Donation() {}

    public Donation(String orderId, long broadcastId, User donor, long streamerId, long amount, String message) {
        this.orderId = orderId;
        this.broadcastId = broadcastId;
        this.donor = donor;
        this.streamerId = streamerId;
        this.amount = amount;
        this.message = message;
        this.status = DonationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /** PENDING일 때만 DONE으로 바꾼다. 이미 승인된 주문이면 false (중복 적립 방지). */
    public boolean approve(String paymentKey) {
        if (status != DonationStatus.PENDING) return false;
        this.paymentKey = paymentKey;
        this.status = DonationStatus.DONE;
        this.approvedAt = Instant.now();
        return true;
    }

    public void cancel() {
        this.status = DonationStatus.CANCELED;
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getPaymentKey() { return paymentKey; }
    public Long getBroadcastId() { return broadcastId; }
    public User getDonor() { return donor; }
    public Long getStreamerId() { return streamerId; }
    public long getAmount() { return amount; }
    public String getMessage() { return message; }
    public DonationStatus getStatus() { return status; }
    public Instant getApprovedAt() { return approvedAt; }
}
