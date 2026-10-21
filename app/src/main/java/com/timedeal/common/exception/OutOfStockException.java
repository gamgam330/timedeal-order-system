package com.timedeal.common.exception;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(Long productId, int stock, int requested) {
        super("out of stock: productId=" + productId + ", stock=" + stock + ", requested=" + requested);
    }
}
