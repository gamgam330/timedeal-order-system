package com.timedeal.product.domain;

import com.timedeal.common.exception.OutOfStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 읽기-계산-쓰기라서 동시에 호출되면 갱신 손실이 생긴다. 호출하는 쪽에서 락으로 감싸야 한다.
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new OutOfStockException(id, stock, quantity);
        }
        this.stock -= quantity;
    }

    public void increaseStock(int quantity) {
        this.stock += quantity;
    }
}
