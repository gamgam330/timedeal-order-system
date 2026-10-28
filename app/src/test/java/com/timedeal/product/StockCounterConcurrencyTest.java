package com.timedeal.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.timedeal.product.service.StockCounter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 수량이 섞인 주문이 동시에 몰릴 때 Redis 재고 숫자가 "남은 재고가 있는데 1개짜리 주문을 거절"하지 않는지 본다.
// 재고는 늘어나지 않으므로(되돌리기 없음), 1개짜리 주문이 거절됐다면 끝났을 때 재고는 0이어야 한다.
@SpringBootTest
@Testcontainers
class StockCounterConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11");

    // 전용 모듈이 없어서 GenericContainer로 띄운다. name = "redis"로 지정하면 spring.data.redis.* 접속 정보가 자동으로 들어간다
    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.11").withExposedPorts(6379);

    static final long PRODUCT_ID = 900L;
    static final int STOCK = 10;
    static final int USERS = 40; // 절반은 3개, 절반은 1개를 주문한다
    static final int ROUNDS = 200;

    @Autowired
    StockCounter stockCounter;

    @Test
    void 수량이_섞여도_재고가_남은_채로_1개짜리_주문을_거절하지_않는다() throws InterruptedException {
        int wrongRejections = 0;

        for (int round = 0; round < ROUNDS; round++) {
            stockCounter.load(PRODUCT_ID, STOCK);
            AtomicInteger rejectedSingles = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(USERS);

            try (ExecutorService executor = Executors.newFixedThreadPool(USERS)) {
                for (int i = 0; i < USERS; i++) {
                    int quantity = i % 2 == 0 ? 3 : 1;
                    executor.submit(() -> {
                        try {
                            start.await();
                            if (!stockCounter.tryDecrease(PRODUCT_ID, quantity) && quantity == 1) {
                                rejectedSingles.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                done.await();
            }

            long left = stockCounter.current(PRODUCT_ID);
            assertThat(left).as("남은 숫자는 음수가 아니어야 한다").isNotNegative();
            if (rejectedSingles.get() > 0 && left > 0) {
                wrongRejections++;
            }
        }

        System.out.printf("재고가 남았는데 1개짜리 주문을 거절한 라운드: %d / %d%n", wrongRejections, ROUNDS);
        assertThat(wrongRejections).isZero();
    }
}
