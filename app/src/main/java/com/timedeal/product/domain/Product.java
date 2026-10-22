package com.timedeal.product.domain;

import com.timedeal.common.exception.OutOfStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    // 낙관적 락: Hibernate가 UPDATE ... SET version = version + 1 WHERE id = ? AND version = ? 를 실행한다.
    // 그 사이 다른 트랜잭션이 먼저 고쳤으면 0행이 갱신되고, 커밋할 때 예외가 난다.
    @Version
    private Long version;

    // 조회한 값으로 계산해서 덮어쓴다 (read-modify-write). 충돌은 @Version이 감지한다.
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
