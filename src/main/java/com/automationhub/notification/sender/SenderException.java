package com.automationhub.notification.sender;

public class SenderException extends RuntimeException {

    public SenderException(String message) {
        super(message);
    }

    public SenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
