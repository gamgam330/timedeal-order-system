package com.timedeal.order.service;

import com.timedeal.common.exception.NotFoundException;
import com.timedeal.common.exception.PaymentFailedException;
import com.timedeal.order.domain.Order;
import com.timedeal.order.domain.OrderStatus;
import com.timedeal.order.repository.OrderRepository;
import com.timedeal.payment.service.PaymentService;
import com.timedeal.product.domain.Product;
import com.timedeal.product.service.ProductService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    // 낙관적 락 충돌 시 재시도 횟수와 대기 시간. 경합이 심하면 이 횟수를 다 써도 실패할 수 있다.
    private static final int MAX_ATTEMPTS = 30;
    private static final int BACKOFF_MIN_MS = 5;
    private static final int BACKOFF_MAX_MS = 30;

    private final OrderRepository orderRepository;
    // 다른 도메인은 Repository가 아니라 Service를 통해서만 호출한다
    private final ProductService productService;
    private final PaymentService paymentService;
    // 같은 클래스 안에서 @Transactional 메서드를 호출하면 프록시를 거치지 않아 적용되지 않는다(self-invocation).
    // 그래서 트랜잭션 경계를 코드로 직접 연다.
    private final TransactionTemplate transactionTemplate;
    private final Counter optimisticRetries;

    public OrderService(OrderRepository orderRepository, ProductService productService, PaymentService paymentService,
                        TransactionTemplate transactionTemplate, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.paymentService = paymentService;
        this.transactionTemplate = transactionTemplate;
        this.optimisticRetries = meterRegistry.counter("stock.optimistic.retries");
    }

    /**
     * 주문 흐름. 외부 PG 호출을 DB 트랜잭션 밖으로 뺐다. (troubleshooting 001)
     * [트랜잭션1] 재고 예약 + 주문 PENDING → PG 승인(커넥션 없음) → [트랜잭션2] 결제 저장 + 주문 PAID
     * PG가 실패하면 이미 커밋된 트랜잭션1을 롤백할 수 없으므로 [트랜잭션3] 재고 복구 + 주문 FAILED 로 보상한다.
     *
     * 알려진 한계: PG 승인 후 트랜잭션2가 실패하거나 그 사이 서버가 죽으면
     * 결제는 됐는데 주문이 PENDING으로 남는다. → 07단계(멱등성), 08단계(사가)에서 다룬다.
     */
    public Order placeOrder(Long userId, Long productId, int quantity) {
        // 재시도할 때는 트랜잭션을 통째로 새로 연다. 실패한 트랜잭션 안에서 다시 읽어도 같은 (낡은) 값이 보이기 때문이다.
        Order order = retryOnConflict(() -> transactionTemplate.execute(status -> {
            Product product = productService.decreaseStock(productId, quantity);
            return orderRepository.save(Order.create(userId, productId, quantity, product.getPrice()));
        }));

        String pgTransactionId;
        try {
            pgTransactionId = paymentService.requestApproval(order.getId(), order.getTotalPrice());
        } catch (PaymentFailedException e) {
            retryOnConflict(() -> transactionTemplate.execute(status -> {
                productService.increaseStock(productId, quantity);
                findOrder(order.getId()).markFailed();
                return null;
            }));
            throw e;
        }

        return transactionTemplate.execute(status -> {
            paymentService.saveApproved(order.getId(), order.getTotalPrice(), pgTransactionId);
            Order paid = findOrder(order.getId());
            paid.markPaid();
            return paid;
        });
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return findOrder(orderId);
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(Long userId, OrderStatus status, Long cursor, int size) {
        long effectiveCursor = cursor == null ? Long.MAX_VALUE : cursor;
        if (status == null) {
            return orderRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, effectiveCursor, Limit.of(size));
        }
        return orderRepository.findByUserIdAndStatusAndIdLessThanOrderByIdDesc(
                userId, status, effectiveCursor, Limit.of(size));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("order", orderId));
    }

    private <T> T retryOnConflict(Supplier<T> transaction) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.get();
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                optimisticRetries.increment();
                sleep(ThreadLocalRandom.current().nextInt(BACKOFF_MIN_MS, BACKOFF_MAX_MS + 1));
            }
        }
    }

    // 모두가 같은 간격으로 재시도하면 또 동시에 부딪히므로, 대기 시간을 무작위로 흩뜨린다 (jitter)
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
