package com.daara.internatadmin.common;

public class AmountMismatchException extends RuntimeException {
    public AmountMismatchException(String message) {
        super(message);
    }
}