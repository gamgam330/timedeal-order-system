package com.timedeal.product.service;

import com.timedeal.common.exception.NotFoundException;
import com.timedeal.common.exception.OutOfStockException;
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
     * 원자적 UPDATE: 조건부 UPDATE 한 문장으로 차감하므로 락을 직접 걸거나 재시도할 필요가 없다.
     */
    @Transactional
    public Product decreaseStock(Long productId, int quantity) {
        if (productRepository.decreaseStock(productId, quantity) == 0) {
            Product product = getProduct(productId); // 없는 상품이면 여기서 404
            throw new OutOfStockException(productId, product.getStock(), quantity);
        }
        return getProduct(productId);
    }

    /** 결제 실패 시 예약했던 재고를 되돌린다 (보상). 차감과 같은 방식(원자적 UPDATE)이어야 여기서도 갱신 손실이 생기지 않는다. */
    @Transactional
    public void increaseStock(Long productId, int quantity) {
        if (productRepository.increaseStock(productId, quantity) == 0) {
            throw new NotFoundException("product", productId);
        }
    }
}
