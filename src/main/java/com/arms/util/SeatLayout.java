package com.arms.util;

import com.arms.model.FareClass;

import java.util.ArrayList;
import java.util.List;

/**
 * The cabin of a narrow-body jet (144 seats):
 * First rows 1-2 and Business rows 3-6 are 2+2 (A C | D F); Economy rows 10-29 are 3+3 (A B C | D E F).
 */
public final class SeatLayout {

    public record SeatSpec(String seatNo, FareClass fareClass) {
    }

    private SeatLayout() {
    }

    public static List<SeatSpec> standard() {
        List<SeatSpec> seats = new ArrayList<>();
        addRows(seats, 1, 2, "ACDF", FareClass.FIRST);
        addRows(seats, 3, 6, "ACDF", FareClass.BUSINESS);
        addRows(seats, 10, 29, "ABCDEF", FareClass.ECONOMY);
        return seats;
    }

    /** Letters in a row for a class, used to draw the seat map with an aisle in the middle. */
    public static String letters(FareClass fareClass) {
        return fareClass == FareClass.ECONOMY ? "ABCDEF" : "ACDF";
    }

    private static void addRows(List<SeatSpec> seats, int first, int last, String letters, FareClass fareClass) {
        for (int row = first; row <= last; row++) {
            for (char letter : letters.toCharArray()) seats.add(new SeatSpec(row + String.valueOf(letter), fareClass));
        }
    }
}
