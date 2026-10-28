package com.timedeal.order.service;

import com.timedeal.common.exception.NotFoundException;
import com.timedeal.common.exception.OutOfStockException;
import com.timedeal.common.exception.PaymentFailedException;
import com.timedeal.order.domain.Order;
import com.timedeal.order.domain.OrderStatus;
import com.timedeal.order.repository.OrderRepository;
import com.timedeal.payment.service.PaymentService;
import com.timedeal.product.domain.Product;
import com.timedeal.product.service.ProductService;
import com.timedeal.product.service.StockCounter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    // 다른 도메인은 Repository가 아니라 Service를 통해서만 호출한다
    private final ProductService productService;
    private final PaymentService paymentService;
    private final StockCounter stockCounter;
    // 같은 클래스 안에서 @Transactional 메서드를 호출하면 프록시를 거치지 않아 적용되지 않는다(self-invocation).
    // 그래서 트랜잭션 경계를 코드로 직접 연다.
    private final TransactionTemplate transactionTemplate;

    /**
     * 주문 흐름. 외부 PG 호출을 DB 트랜잭션 밖으로 뺐다. (troubleshooting 001)
     * [트랜잭션1] 재고 예약 + 주문 PENDING → PG 승인(커넥션 없음) → [트랜잭션2] 결제 저장 + 주문 PAID
     * PG가 실패하면 이미 커밋된 트랜잭션1을 롤백할 수 없으므로 [트랜잭션3] 재고 복구 + 주문 FAILED 로 보상한다.
     *
     * 트랜잭션1 전에 Redis 재고 숫자부터 줄인다. 모자라면 DB에 가지 않고 바로 품절로 응답한다. (troubleshooting 004)
     * Redis 숫자가 DB 재고보다 커지지 않게 순서를 지킨다: 줄일 때는 Redis 먼저, 되돌릴 때는 DB를 되돌린 뒤 Redis.
     * 트랜잭션1이 실패하거나 PG가 실패하면 Redis 숫자도 되돌린다. 되돌리기 전에 서버가 죽으면 Redis가 DB보다 작게 남는다(덜 팔림).
     *
     * 알려진 한계: PG 승인 후 트랜잭션2가 실패하거나 그 사이 서버가 죽으면
     * 결제는 됐는데 주문이 PENDING으로 남는다. → 08단계(멱등성), 09단계(사가)에서 다룬다.
     */
    public Order placeOrder(Long userId, Long productId, int quantity) {
        if (!stockCounter.tryDecrease(productId, quantity)) {
            throw new OutOfStockException(productId, quantity);
        }

        Order order;
        try {
            order = transactionTemplate.execute(status -> {
                Product product = productService.decreaseStock(productId, quantity);
                return orderRepository.save(Order.create(userId, productId, quantity, product.getPrice()));
            });
        } catch (RuntimeException e) {
            stockCounter.increase(productId, quantity); // 트랜잭션1은 롤백됐으므로 DB 재고는 그대로다
            throw e;
        }

        String pgTransactionId;
        try {
            pgTransactionId = paymentService.requestApproval(order.getId(), order.getTotalPrice());
        } catch (PaymentFailedException e) {
            transactionTemplate.executeWithoutResult(status -> {
                productService.increaseStock(productId, quantity);
                findOrder(order.getId()).markFailed();
            });
            stockCounter.increase(productId, quantity); // DB 재고를 되돌린 뒤에 Redis를 늘린다
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
}
