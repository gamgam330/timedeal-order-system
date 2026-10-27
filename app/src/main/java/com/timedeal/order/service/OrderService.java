package com.timedeal.order.service;

import com.timedeal.common.exception.NotFoundException;
import com.timedeal.common.exception.PaymentFailedException;
import com.timedeal.order.domain.Order;
import com.timedeal.order.domain.OrderStatus;
import com.timedeal.order.repository.OrderRepository;
import com.timedeal.payment.service.PaymentService;
import com.timedeal.product.domain.Product;
import com.timedeal.product.service.ProductService;
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
    // 같은 클래스 안에서 @Transactional 메서드를 호출하면 프록시를 거치지 않아 적용되지 않는다(self-invocation).
    // 그래서 트랜잭션 경계를 코드로 직접 연다.
    private final TransactionTemplate transactionTemplate;

    /**
     * 주문 흐름. 외부 PG 호출을 DB 트랜잭션 밖으로 뺐다. (troubleshooting 001)
     * [트랜잭션1] 재고 예약 + 주문 PENDING → PG 승인(커넥션 없음) → [트랜잭션2] 결제 저장 + 주문 PAID
     * PG가 실패하면 이미 커밋된 트랜잭션1을 롤백할 수 없으므로 [트랜잭션3] 재고 복구 + 주문 FAILED 로 보상한다.
     *
     * 알려진 한계: PG 승인 후 트랜잭션2가 실패하거나 그 사이 서버가 죽으면
     * 결제는 됐는데 주문이 PENDING으로 남는다. → 08단계(멱등성), 09단계(사가)에서 다룬다.
     */
    public Order placeOrder(Long userId, Long productId, int quantity) {
        Order order = transactionTemplate.execute(status -> {
            Product product = productService.decreaseStock(productId, quantity);
            return orderRepository.save(Order.create(userId, productId, quantity, product.getPrice()));
        });

        String pgTransactionId;
        try {
            pgTransactionId = paymentService.requestApproval(order.getId(), order.getTotalPrice());
        } catch (PaymentFailedException e) {
            transactionTemplate.executeWithoutResult(status -> {
                productService.increaseStock(productId, quantity);
                findOrder(order.getId()).markFailed();
            });
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
