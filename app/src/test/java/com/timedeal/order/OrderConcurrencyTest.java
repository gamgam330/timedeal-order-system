package com.timedeal.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.timedeal.common.exception.OutOfStockException;
import com.timedeal.order.service.OrderService;
import com.timedeal.product.service.ProductService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 재고 50개에 100명이 동시에 주문한다. 정확히 50명만 성공하고 재고는 0이어야 한다.
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"pg.latency-ms=0", "pg.failure-rate=0.0"})
class OrderConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11");

    // 전용 모듈이 없어서 GenericContainer로 띄운다. name = "redis"로 지정하면 spring.data.redis.* 접속 정보가 자동으로 들어간다
    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.11").withExposedPorts(6379);

    static final long PRODUCT_ID = 100L;
    static final int STOCK = 50;
    static final int USERS = 100;

    @Autowired
    OrderService orderService;

    @Autowired
    ProductService productService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 동시에_주문해도_재고보다_많이_팔리지_않는다() throws InterruptedException {
        jdbcTemplate.update("INSERT INTO product (id, name, price, stock, created_at) VALUES (?, '동시성 테스트', 1000, ?, NOW(6))",
                PRODUCT_ID, STOCK);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(USERS);

        try (ExecutorService executor = Executors.newFixedThreadPool(USERS)) {
            for (int i = 0; i < USERS; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        start.await(); // 모든 스레드가 준비된 뒤 한꺼번에 출발시킨다
                        orderService.placeOrder(userId, PRODUCT_ID, 1);
                        success.incrementAndGet();
                    } catch (OutOfStockException e) {
                        soldOut.incrementAndGet();
                    } catch (Exception e) {
                        error.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
        }

        assertThat(error.get()).as("예상하지 못한 예외").isZero();
        assertThat(success.get()).as("주문 성공").isEqualTo(STOCK);
        assertThat(soldOut.get()).as("품절 응답").isEqualTo(USERS - STOCK);
        assertThat(productService.getProduct(PRODUCT_ID).getStock()).as("남은 재고").isZero();
    }
}
