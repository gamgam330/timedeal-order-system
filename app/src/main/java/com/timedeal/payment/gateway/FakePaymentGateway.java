package com.timedeal.payment.gateway;

import com.timedeal.common.exception.PaymentFailedException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 외부 PG사를 흉내 낸다. 지연 시간과 실패율을 설정값으로 바꿔서 장애 상황을 재현한다.
 * - 01단계: 지연을 늘려 커넥션 고갈 재현
 * - 07단계: 타임아웃과 재시도로 인한 중복 결제 재현
 * - 08단계: 결제 실패로 보상 트랜잭션 확인
 */
@Component
@RequiredArgsConstructor
public class FakePaymentGateway {

    private final PgProperties properties;

    public String approve(Long orderId, long amount) {
        sleep(properties.latencyMs());
        if (ThreadLocalRandom.current().nextDouble() < properties.failureRate()) {
            throw new PaymentFailedException(orderId);
        }
        return UUID.randomUUID().toString();
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
