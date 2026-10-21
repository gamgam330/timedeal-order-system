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
     */
    @Transactional
    public Product decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("product", productId));
        product.decreaseStock(quantity);
        return product;
    }

    /** 결제 실패 시 예약했던 재고를 되돌린다 (보상). */
    @Transactional
    public void increaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("product", productId));
        product.increaseStock(quantity);
    }
}
