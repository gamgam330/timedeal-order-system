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
     * 비관적 락: 행을 잠근 채로 읽기 때문에, 커밋될 때까지 다른 주문은 같은 재고를 읽지 못하고 기다린다.
     */
    @Transactional
    public Product decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("product", productId));
        product.decreaseStock(quantity);
        return product;
    }

    /** 결제 실패 시 예약했던 재고를 되돌린다 (보상). 차감과 같은 락을 걸어야 여기서도 갱신 손실이 생기지 않는다. */
    @Transactional
    public void increaseStock(Long productId, int quantity) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new NotFoundException("product", productId));
        product.increaseStock(quantity);
    }
}
