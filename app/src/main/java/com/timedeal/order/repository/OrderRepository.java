package com.timedeal.order.repository;

import com.timedeal.order.domain.Order;
import com.timedeal.order.domain.OrderStatus;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 커서 기반 페이지네이션: id < cursor 조건으로 다음 페이지를 가져온다 (OFFSET을 쓰지 않음)
    List<Order> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Limit limit);

    List<Order> findByUserIdAndStatusAndIdLessThanOrderByIdDesc(Long userId, OrderStatus status, Long cursor, Limit limit);
}
