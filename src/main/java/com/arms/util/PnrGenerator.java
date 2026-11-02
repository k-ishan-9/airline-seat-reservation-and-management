package com.arms.util;

import java.security.SecureRandom;

/**
 * 6-character booking references like "K7MX2Q", the format airlines use.
 * Ambiguous characters (0/O, 1/I/L) are left out so references can be read over the phone.
 * Uniqueness is enforced by a UNIQUE key on bookings.pnr; on a rare clash the caller retries.
 */
public final class PnrGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PnrGenerator() {
    }

    public static String next() {
        StringBuilder pnr = new StringBuilder(6);
        for (int i = 0; i < 6; i++) pnr.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return pnr.toString();
    }
}
