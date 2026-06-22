package com.automationhub.payment.exception;

public class StripeNotConfiguredException extends RuntimeException {

    public StripeNotConfiguredException(String message) {
        super(message);
    }
}
