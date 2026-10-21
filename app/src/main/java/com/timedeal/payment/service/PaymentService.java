package com.timedeal.payment.service;

import com.timedeal.payment.domain.Payment;
import com.timedeal.payment.gateway.FakePaymentGateway;
import com.timedeal.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FakePaymentGateway paymentGateway;

    /**
     * 외부 PG에 승인을 요청한다. DB를 쓰지 않으므로 트랜잭션을 열지 않는다.
     * 호출하는 쪽도 트랜잭션 밖에서 불러야 PG를 기다리는 동안 커넥션을 쥐고 있지 않는다.
     */
    public String requestApproval(Long orderId, long amount) {
        return paymentGateway.approve(orderId, amount);
    }

    @Transactional
    public Payment saveApproved(Long orderId, long amount, String pgTransactionId) {
        return paymentRepository.save(Payment.approved(orderId, amount, pgTransactionId));
    }
}
