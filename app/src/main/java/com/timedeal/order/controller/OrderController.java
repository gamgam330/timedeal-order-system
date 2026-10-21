package com.timedeal.order.controller;

import com.timedeal.order.domain.Order;
import com.timedeal.order.domain.OrderStatus;
import com.timedeal.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return OrderResponse.from(orderService.placeOrder(request.userId(), request.productId(), request.quantity()));
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        return OrderResponse.from(orderService.getOrder(orderId));
    }

    @GetMapping("/api/users/{userId}/orders")
    public UserOrdersResponse getUserOrders(
            @PathVariable Long userId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        List<OrderResponse> orders = orderService.getUserOrders(userId, status, cursor, size).stream()
                .map(OrderResponse::from)
                .toList();
        Long nextCursor = orders.size() < size ? null : orders.getLast().id();
        return new UserOrdersResponse(orders, nextCursor);
    }

    public record PlaceOrderRequest(
            @NotNull Long userId,
            @NotNull Long productId,
            @Min(1) @Max(10) int quantity) {
    }

    public record OrderResponse(
            Long id, Long userId, Long productId, int quantity, long totalPrice,
            OrderStatus status, LocalDateTime createdAt) {
        static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getUserId(), order.getProductId(), order.getQuantity(),
                    order.getTotalPrice(), order.getStatus(), order.getCreatedAt());
        }
    }

    public record UserOrdersResponse(List<OrderResponse> orders, Long nextCursor) {
    }
}
