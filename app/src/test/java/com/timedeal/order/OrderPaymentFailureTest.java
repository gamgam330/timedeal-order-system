package com.timedeal.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.timedeal.common.exception.PaymentFailedException;
import com.timedeal.order.domain.OrderStatus;
import com.timedeal.order.repository.OrderRepository;
import com.timedeal.order.service.OrderService;
import com.timedeal.payment.repository.PaymentRepository;
import com.timedeal.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// PG가 항상 실패하는 환경: 트랜잭션1은 이미 커밋됐으므로 롤백이 아니라 보상(재고 복구)이 일어나야 한다
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"pg.latency-ms=0", "pg.failure-rate=1.0"})
class OrderPaymentFailureTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11");

    // 전용 모듈이 없어서 GenericContainer로 띄운다. name = "redis"로 지정하면 spring.data.redis.* 접속 정보가 자동으로 들어간다
    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.11").withExposedPorts(6379);

    @Autowired
    OrderService orderService;

    @Autowired
    ProductService productService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    void 결제가_실패하면_재고가_복구되고_주문은_FAILED로_남는다() {
        int before = productService.getProduct(1L).getStock();

        assertThatThrownBy(() -> orderService.placeOrder(1L, 1L, 3))
                .isInstanceOf(PaymentFailedException.class);

        assertThat(productService.getProduct(1L).getStock()).isEqualTo(before);
        assertThat(orderRepository.findAll())
                .singleElement()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED));
        assertThat(paymentRepository.count()).isZero();
    }
}
