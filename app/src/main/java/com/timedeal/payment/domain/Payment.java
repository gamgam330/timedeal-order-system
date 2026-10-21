package com.timedeal.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private String pgTransactionId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Payment approved(Long orderId, long amount, String pgTransactionId) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.amount = amount;
        payment.status = PaymentStatus.APPROVED;
        payment.pgTransactionId = pgTransactionId;
        payment.createdAt = LocalDateTime.now();
        return payment;
    }
}
