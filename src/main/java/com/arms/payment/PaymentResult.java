package com.arms.payment;

public record PaymentResult(boolean approved, String reference, String message) {
}
