package com.daara.internatadmin.common;

public class PartialPaymentException extends RuntimeException {
    public PartialPaymentException(String message) {
        super(message);
    }
}