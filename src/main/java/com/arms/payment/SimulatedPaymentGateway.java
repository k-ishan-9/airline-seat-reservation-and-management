package com.arms.payment;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * A stand-in payment provider with realistic checks.
 * Test values: card 4111 1111 1111 1111 is approved; 4000 0000 0000 0002 is declined;
 * a UPI id starting with "fail" is declined.
 */
public final class SimulatedPaymentGateway implements PaymentGateway {

    private static final BigDecimal LIMIT = new BigDecimal("500000");
    private static final Pattern UPI = Pattern.compile("^[a-z0-9._-]{2,64}@[a-z]{2,32}$");
    private final SecureRandom random = new SecureRandom();

    @Override
    public PaymentResult charge(BigDecimal amount, PaymentMethod method, String bookingReference) {
        if (amount.signum() <= 0) return declined("Invalid amount");
        if (amount.compareTo(LIMIT) > 0) return declined("Amount exceeds the per-transaction limit");

        return switch (method) {
            case PaymentMethod.Card card -> {
                if (!card.number().matches("\\d{13,19}") || !passesLuhn(card.number())) {
                    yield declined("Invalid card number");
                }
                if (card.number().endsWith("0002")) yield declined("Card declined by the bank");
                yield approved();
            }
            case PaymentMethod.Upi upi -> {
                if (!UPI.matcher(upi.vpa()).matches()) yield declined("Invalid UPI ID");
                if (upi.vpa().startsWith("fail")) yield declined("UPI payment declined");
                yield approved();
            }
        };
    }

    /** The Luhn checksum every real card number satisfies; catches most typos. */
    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    private PaymentResult approved() {
        byte[] bytes = new byte[5];
        random.nextBytes(bytes);
        return new PaymentResult(true, "TXN" + HexFormat.of().formatHex(bytes).toUpperCase(), "Approved");
    }

    private static PaymentResult declined(String reason) {
        return new PaymentResult(false, null, reason);
    }
}
