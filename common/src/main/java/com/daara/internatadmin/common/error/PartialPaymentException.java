package com.daara.internatadmin.common.error;

public class PartialPaymentException extends RuntimeException {
    public PartialPaymentException(String message) {
        super(message);
    }
}
