package com.timedeal.product.service;

import com.timedeal.common.exception.NotFoundException;
import com.timedeal.product.domain.Product;
import com.timedeal.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("product", productId));
    }

    /**
     * 재고를 차감하고, 차감된 상품을 돌려준다.
     * 호출한 쪽(OrderService)의 트랜잭션에 참여한다 (기본 전파 속성 REQUIRED).
     * 읽기-계산-쓰기라서 호출하는 쪽이 분산 락으로 트랜잭션 전체를 감싸야 한다.
     * 여기서 락을 잡고 풀면 커밋 전에 락이 풀려서 갱신 손실이 그대로 생긴다. (troubleshooting 003)
     */
    @Transactional
    public Product decreaseStock(Long productId, int quantity) {
        Product product = getProduct(productId);
        product.decreaseStock(quantity);
        return product;
    }

    /** 결제 실패 시 예약했던 재고를 되돌린다 (보상). 차감과 마찬가지로 호출하는 쪽이 락으로 감싸야 한다. */
    @Transactional
    public void increaseStock(Long productId, int quantity) {
        getProduct(productId).increaseStock(quantity);
    }

    /** 재고를 바꾸는 트랜잭션을 감쌀 분산 락의 키 */
    public static String stockLockKey(Long productId) {
        return "lock:product:" + productId;
    }
}
