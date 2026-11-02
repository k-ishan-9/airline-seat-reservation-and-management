package com.arms.model;

public record Seat(long id, String seatNo, FareClass fareClass, boolean taken) {
    /** "12A" -> 12 */
    public int row() {
        return Integer.parseInt(seatNo.replaceAll("[^0-9]", ""));
    }

    /** "12A" -> 'A' */
    public char letter() {
        return seatNo.charAt(seatNo.length() - 1);
    }
}
