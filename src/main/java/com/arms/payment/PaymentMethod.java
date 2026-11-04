package com.arms.payment;

/**
 * How a customer pays. A sealed interface: the compiler knows these are the only options,
 * so a switch over them must handle both.
 */
public sealed interface PaymentMethod permits PaymentMethod.Card, PaymentMethod.Upi {

    /** Safe text to store and print, e.g. "CARD ****1111". Card numbers are never stored. */
    String masked();

    record Card(String number, String holder) implements PaymentMethod {
        public Card {
            number = number == null ? "" : number.replaceAll("[\\s-]", "");
        }

        @Override
        public String masked() {
            String last4 = number.length() >= 4 ? number.substring(number.length() - 4) : number;
            return "CARD ****" + last4;
        }
    }

    record Upi(String vpa) implements PaymentMethod {
        public Upi {
            vpa = vpa == null ? "" : vpa.trim().toLowerCase();
        }

        @Override
        public String masked() {
            int at = vpa.indexOf('@');
            return at > 0 ? "UPI " + vpa.charAt(0) + "***" + vpa.substring(at) : "UPI";
        }
    }
}
