package com.arms.payment;

import java.math.BigDecimal;

/**
 * The boundary to an external payment provider. The booking code depends only on this
 * interface, so a real provider (Razorpay, Stripe) can replace the simulator without touching it.
 */
public interface PaymentGateway {
    PaymentResult charge(BigDecimal amount, PaymentMethod method, String bookingReference);
}
