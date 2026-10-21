package com.timedeal.common.exception;

public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(Long orderId) {
        super("payment failed: orderId=" + orderId);
    }
}
