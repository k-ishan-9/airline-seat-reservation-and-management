package com.arms.model;

import java.util.Arrays;

public enum FareClass {
    ECONOMY("Economy"), BUSINESS("Business"), FIRST("First");

    private final String label;

    FareClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Accepts "economy", "E", "business", "B", "first", "F" (any case). */
    public static FareClass parse(String text) {
        String t = text.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(c -> c.name().equals(t) || c.name().startsWith(t) && !t.isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown class: " + text));
    }
}
