package com.timedeal.product.service;

import com.timedeal.common.exception.NotFoundException;
import com.timedeal.common.lock.RedisLock;
import com.timedeal.product.domain.Product;
import com.timedeal.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String LOCK_KEY_PREFIX = "lock:product:";

    private final ProductRepository productRepository;
    private final RedisLock redisLock;

    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("product", productId));
    }

    /**
     * 재고를 차감하고, 차감된 상품을 돌려준다.
     * 호출한 쪽(OrderService)의 트랜잭션에 참여한다 (기본 전파 속성 REQUIRED).
     * 분산 락: 같은 상품의 재고 계산은 한 번에 한 요청만 하도록 Redis 락으로 감싼다.
     */
    @Transactional
    public Product decreaseStock(Long productId, int quantity) {
        return redisLock.executeWithLock(LOCK_KEY_PREFIX + productId, () -> {
            Product product = getProduct(productId);
            product.decreaseStock(quantity);
            return product;
        });
    }

    /** 결제 실패 시 예약했던 재고를 되돌린다 (보상). 차감과 같은 락을 써야 여기서도 갱신 손실이 생기지 않는다. */
    @Transactional
    public void increaseStock(Long productId, int quantity) {
        redisLock.executeWithLock(LOCK_KEY_PREFIX + productId, () -> {
            getProduct(productId).increaseStock(quantity);
            return null;
        });
    }
}
