package com.timedeal.product.repository;

import com.timedeal.product.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // SELECT ... FOR UPDATE: 트랜잭션이 끝날 때까지 이 행을 잠근다. 다른 트랜잭션의 FOR UPDATE는 기다린다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);
}
