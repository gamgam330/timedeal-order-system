package com.timedeal.order.service;

import com.timedeal.common.exception.NotFoundException;
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

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    // 다른 도메인은 Repository가 아니라 Service를 통해서만 호출한다
    private final ProductService productService;
    private final PaymentService paymentService;

    /**
     * 00단계의 순진한 주문 흐름. 전부 트랜잭션 하나로 묶여 있다.
     * 재고 예약 → 주문 생성 → 결제 → 주문 완료
     * 결제가 실패하면 예외가 전파되어 재고 차감까지 전부 롤백된다.
     */
    @Transactional
    public Order placeOrder(Long userId, Long productId, int quantity) {
        Product product = productService.decreaseStock(productId, quantity);

        Order order = orderRepository.save(Order.create(userId, productId, quantity, product.getPrice()));

        // 외부 PG 호출이 DB 트랜잭션 안에 있다 → PG가 느려지면 그동안 DB 커넥션을 붙잡고 있다
        paymentService.pay(order.getId(), order.getTotalPrice());

        order.markPaid();
        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("order", orderId));
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
}
