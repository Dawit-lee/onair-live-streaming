package com.onair.donation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onair.auth.UserRepository;
import com.onair.broadcast.BroadcastRepository;
import com.onair.common.ApiException;

@Service
public class DonationService {
    static final long MIN_AMOUNT = 1_000;
    static final long MAX_AMOUNT = 1_000_000;

    private final DonationRepository donations;
    private final BroadcastRepository broadcasts;
    private final UserRepository users;
    private final TossPaymentsClient toss;

    public DonationService(
            DonationRepository donations, BroadcastRepository broadcasts, UserRepository users, TossPaymentsClient toss) {
        this.donations = donations;
        this.broadcasts = broadcasts;
        this.users = users;
        this.toss = toss;
    }

    public record OrderResponse(String orderId, long amount, String orderName, String clientKey, String customerKey) {}

    public record DonationItem(long id, String donorNickname, long amount, String message, java.time.Instant approvedAt) {}

    public record Summary(long total, long count, List<DonationItem> items) {}

    /** 1단계: 서버가 주문(후원 대상, 금액)을 먼저 저장한다. 승인 시 이 값과 대조한다. */
    @Transactional
    public OrderResponse createOrder(long donorId, long broadcastId, long amount, String message) {
        if (!toss.enabled()) throw ApiException.badRequest("결제 키가 설정되지 않았습니다.");
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw ApiException.badRequest("후원 금액은 1,000원 이상 1,000,000원 이하입니다.");
        }
        var broadcast = broadcasts.findById(broadcastId).orElseThrow(() -> ApiException.notFound("방송"));
        long streamerId = broadcast.getOwner().getId();
        if (streamerId == donorId) throw ApiException.badRequest("자신의 방송에는 후원할 수 없습니다.");
        var donor = users.getReferenceById(donorId);
        String orderId = "don_" + UUID.randomUUID().toString().replace("-", "");
        donations.save(new Donation(orderId, broadcastId, donor, streamerId, amount, message));
        String orderName = broadcast.getOwner().getNickname() + "님 후원";
        return new OrderResponse(orderId, amount, orderName, toss.clientKey(), "user_" + donorId);
    }

    /**
     * 2단계: 결제창 성공 리다이렉트 후 호출. 금액 위변조를 막기 위해 저장된 금액과 비교하고, 토스 승인 API 응답의 상태/금액/주문번호를
     * 다시 확인한 뒤 확정한다. 같은 주문으로 여러 번 호출돼도 한 번만 적립된다.
     */
    @Transactional
    public DonationItem confirm(long donorId, String paymentKey, String orderId, long amount) {
        Donation d = donations.lockByOrderId(orderId).orElseThrow(() -> ApiException.notFound("후원 주문"));
        if (d.getDonor().getId() != donorId) throw ApiException.forbidden();
        if (d.getStatus() == DonationStatus.DONE) {
            if (!paymentKey.equals(d.getPaymentKey())) throw ApiException.conflict("이미 다른 결제로 승인된 주문입니다.");
            return item(d);
        }
        if (d.getStatus() != DonationStatus.PENDING) throw ApiException.conflict("취소된 주문입니다.");
        if (d.getAmount() != amount) throw ApiException.badRequest("결제 금액이 주문 금액과 다릅니다.");

        Map<String, Object> payment = toss.confirm(paymentKey, orderId, amount);
        verifyPayment(payment, d);
        d.approve(paymentKey);
        return item(d);
    }

    /**
     * 토스 웹훅(PAYMENT_STATUS_CHANGED). 본문은 신뢰하지 않고 paymentKey로 결제를 다시 조회해 검증한다. 같은 통지가 여러 번 와도
     * approve()가 PENDING일 때만 적립하므로 중복 적립되지 않는다.
     */
    @Transactional
    public void onWebhook(String paymentKey) {
        if (paymentKey == null || !toss.enabled()) return;
        Map<String, Object> payment = toss.get(paymentKey);
        String orderId = String.valueOf(payment.get("orderId"));
        donations.lockByOrderId(orderId).ifPresent(d -> {
            String status = String.valueOf(payment.get("status"));
            if ("DONE".equals(status) && d.getStatus() == DonationStatus.PENDING) {
                verifyPayment(payment, d);
                d.approve(paymentKey);
            } else if (("CANCELED".equals(status) || "ABORTED".equals(status) || "EXPIRED".equals(status))
                    && d.getStatus() != DonationStatus.CANCELED
                    && (d.getPaymentKey() == null || d.getPaymentKey().equals(paymentKey))) {
                d.cancel();
            }
        });
    }

    @Transactional(readOnly = true)
    public Summary summary(long broadcastId) {
        var items = donations.findTop100ByBroadcastIdAndStatusOrderByApprovedAtDesc(broadcastId, DonationStatus.DONE)
                .stream().map(DonationService::item).toList();
        return new Summary(donations.sumDone(broadcastId), donations.countDone(broadcastId), items);
    }

    private static void verifyPayment(Map<String, Object> payment, Donation d) {
        Object total = payment.get("totalAmount");
        boolean ok = "DONE".equals(payment.get("status"))
                && d.getOrderId().equals(payment.get("orderId"))
                && total instanceof Number n && n.longValue() == d.getAmount();
        if (!ok) throw ApiException.badRequest("결제 승인 결과가 주문과 일치하지 않습니다.");
    }

    private static DonationItem item(Donation d) {
        return new DonationItem(d.getId(), d.getDonor().getNickname(), d.getAmount(), d.getMessage(), d.getApprovedAt());
    }
}
