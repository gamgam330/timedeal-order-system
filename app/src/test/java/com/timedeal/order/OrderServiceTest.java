package com.timedeal.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.timedeal.common.exception.OutOfStockException;
import com.timedeal.order.domain.Order;
import com.timedeal.order.domain.OrderStatus;
import com.timedeal.order.service.OrderService;
import com.timedeal.payment.repository.PaymentRepository;
import com.timedeal.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// H2가 아니라 실제 MySQL에서 돌린다 (락 동작이 달라서 03단계 동시성 테스트를 믿을 수 없게 됨)
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"pg.latency-ms=0", "pg.failure-rate=0.0"})
class OrderServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11");

    @Autowired
    OrderService orderService;

    @Autowired
    ProductService productService;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void 주문하면_재고가_줄고_결제가_생성된다() {
        int before = productService.getProduct(1L).getStock();

        Order order = orderService.placeOrder(1L, 1L, 2);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getTotalPrice()).isEqualTo(20_000L);
        assertThat(productService.getProduct(1L).getStock()).isEqualTo(before - 2);
        assertThat(paymentRepository.findAll()).anyMatch(p -> p.getOrderId().equals(order.getId()));
    }

    @Test
    void 재고보다_많이_주문하면_실패한다() {
        assertThatThrownBy(() -> orderService.placeOrder(1L, 1L, 1_000))
                .isInstanceOf(OutOfStockException.class);
    }
}
