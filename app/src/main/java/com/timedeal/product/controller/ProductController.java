package com.timedeal.product.controller;

import com.timedeal.product.domain.Product;
import com.timedeal.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable Long productId) {
        return ProductResponse.from(productService.getProduct(productId));
    }

    public record ProductResponse(Long id, String name, long price, int stock) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStock());
        }
    }
}
