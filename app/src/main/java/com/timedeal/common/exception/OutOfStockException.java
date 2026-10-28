package com.timedeal.common.exception;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(Long productId, int stock, int requested) {
        super("out of stock: productId=" + productId + ", stock=" + stock + ", requested=" + requested);
    }

    // Redis 재고 숫자에서 걸러진 경우. 남은 수량은 확인하지 않는다
    public OutOfStockException(Long productId, int requested) {
        super("out of stock: productId=" + productId + ", requested=" + requested);
    }
}
