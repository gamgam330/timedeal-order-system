package com.timedeal.product.repository;

import com.timedeal.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 읽기-계산-쓰기를 DB 한 문장으로 처리한다. InnoDB는 행에 X락을 걸고 "최신 값"으로 계산하므로 갱신 손실이 없다.
    // 재고 검사도 WHERE 조건이 대신한다: 재고가 모자라면 0행이 갱신된다.
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and p.stock >= :quantity")
    int decreaseStock(Long id, int quantity);

    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
    int increaseStock(Long id, int quantity);
}
