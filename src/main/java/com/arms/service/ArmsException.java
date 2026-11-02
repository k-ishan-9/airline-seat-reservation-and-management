package com.arms.service;

/** Base class for problems the user can understand and fix. The message is shown as-is. */
public class ArmsException extends RuntimeException {
    public ArmsException(String message) {
        super(message);
    }
}
