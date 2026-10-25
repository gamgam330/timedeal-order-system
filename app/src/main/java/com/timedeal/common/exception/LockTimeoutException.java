package com.timedeal.common.exception;

public class LockTimeoutException extends RuntimeException {

    public LockTimeoutException(String key) {
        super("lock wait timeout: key=" + key);
    }
}
