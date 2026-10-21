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

    @Transactional
    public Payment pay(Long orderId, long amount) {
        String pgTransactionId = paymentGateway.approve(orderId, amount);
        return paymentRepository.save(Payment.approved(orderId, amount, pgTransactionId));
    }
}
