package com.arms.util;

import com.arms.model.FareClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PnrAndSeatLayoutTest {

    @Test
    public void pnrsAreSixUnambiguousCharacters() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String pnr = PnrGenerator.next();
            assertTrue(pnr, pnr.matches("[A-HJ-KM-NP-Z2-9]{6}"));
            seen.add(pnr);
        }
        assertTrue("PNRs should practically never repeat", seen.size() > 995);
    }

    @Test
    public void cabinHas144SeatsInThreeClasses() {
        List<SeatLayout.SeatSpec> seats = SeatLayout.standard();
        assertEquals(144, seats.size());
        assertEquals(8, seats.stream().filter(s -> s.fareClass() == FareClass.FIRST).count());
        assertEquals(16, seats.stream().filter(s -> s.fareClass() == FareClass.BUSINESS).count());
        assertEquals(120, seats.stream().filter(s -> s.fareClass() == FareClass.ECONOMY).count());
        assertEquals(144, seats.stream().map(SeatLayout.SeatSpec::seatNo).distinct().count());
    }
}
