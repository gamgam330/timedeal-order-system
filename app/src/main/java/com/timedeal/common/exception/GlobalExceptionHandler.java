package com.timedeal.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // K6에서 "재고 소진"과 "서버 오류"를 상태 코드로 구분하기 위해 409로 응답한다
    @ExceptionHandler(OutOfStockException.class)
    public ProblemDetail handleOutOfStock(OutOfStockException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ProblemDetail handlePaymentFailed(PaymentFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
    }

    // 락을 기다리다 포기한 경우. 잠시 뒤 다시 시도하면 되는 상황이라 503으로 응답한다
    @ExceptionHandler(LockTimeoutException.class)
    public ProblemDetail handleLockTimeout(LockTimeoutException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
