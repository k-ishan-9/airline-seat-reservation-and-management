package com.arms.service;

import com.arms.dao.ReportDao;
import com.arms.model.Booking;
import com.arms.model.FareClass;
import com.arms.model.FlightOffer;
import com.arms.model.Seat;
import com.arms.model.User;
import com.arms.support.IntegrationTest;
import com.arms.support.TestDb;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FlightAndAdminServiceTest extends IntegrationTest {

    private final LocalDate today = START.toLocalDate();

    // ---------- search ----------

    @Test
    public void searchShowsPricesAndLiveSeatCounts() {
        List<FlightOffer> offers = flights.search("del", "BOM", today.plusDays(2));
        assertEquals(1, offers.size());
        assertEquals(120, offers.get(0).fare(FareClass.ECONOMY).seatsLeft());
        assertEquals(new BigDecimal("17100.00"), offers.get(0).fare(FareClass.BUSINESS).price()); // 3 x 5,700

        bookings.book(customer("alice"), request(offers.get(0).flight(), FareClass.ECONOMY,
                List.of("Alice Rao", "Ravi Rao"), List.of(), GOOD_CARD));
        assertEquals(118, flights.search("DEL", "BOM", today.plusDays(2)).get(0).fare(FareClass.ECONOMY).seatsLeft());
    }

    @Test
    public void departedFlightsAreHidden() {
        assertEquals(0, flights.search("DEL", "BOM", today).size()); // 06:30 flight left before 10:00
    }

    @Test
    public void searchValidatesInput() {
        expectValidation(() -> flights.search("DEL", "del", today));
        expectValidation(() -> flights.search("DE", "BOM", today));
        expectValidation(() -> flights.search("ZZZ", "BOM", today));
        expectValidation(() -> flights.search("DEL", "BOM", today.minusDays(1)));
    }

    @Test
    public void budgetSearchReturnsCheapestFirstAndNothingOverBudget() {
        List<FlightOffer> offers = flights.withinBudget(FareClass.ECONOMY, new BigDecimal("4000"), null);
        assertTrue(offers.size() > 0);
        BigDecimal previous = BigDecimal.ZERO;
        for (FlightOffer offer : offers) {
            BigDecimal price = offer.fare(FareClass.ECONOMY).price();
            assertTrue(price.compareTo(new BigDecimal("4000")) <= 0);
            assertTrue(price.compareTo(previous) >= 0);
            previous = price;
        }
        assertEquals("AR402", offers.get(0).flight().flightNo()); // BLR-HYD at Rs 3,200
    }

    @Test
    public void seatMapMarksTakenSeats() {
        var flight = flight("AR101", 2);
        bookings.book(customer("alice"), request(flight, FareClass.FIRST, List.of("Alice Rao"), List.of("1C"), GOOD_CARD));
        List<Seat> map = flights.seatMap(flight.id(), FareClass.FIRST);
        assertEquals(8, map.size());
        assertEquals(List.of("1C"), map.stream().filter(Seat::taken).map(Seat::seatNo).toList());
    }

    // ---------- admin ----------

    @Test
    public void customersCantUseAdminFunctions() {
        User alice = customer("alice");
        try {
            admin.revenueByRoute(alice);
            fail("customers must not see revenue");
        } catch (AuthException expected) {
            // ok
        }
    }

    @Test
    public void adminAddsAFlightWithAFullCabin() {
        User root = adminUser();
        LocalDateTime departs = START.plusDays(3).withHour(15).withMinute(0);
        Map<FareClass, BigDecimal> prices = Map.of(FareClass.ECONOMY, new BigDecimal("4100"),
                FareClass.BUSINESS, new BigDecimal("12000"), FareClass.FIRST, new BigDecimal("20000"));
        long id = admin.addFlight(root, "ar901", "DEL", "HYD", departs, departs.plusMinutes(130), prices);

        assertEquals(144, TestDb.count(db, "SELECT COUNT(*) FROM seats WHERE flight_id = ?", id));
        assertEquals(1, flights.search("DEL", "HYD", departs.toLocalDate()).size());
        expectValidation(() -> admin.addFlight(root, "AR901", "DEL", "HYD", departs, departs.plusMinutes(130), prices));
        expectValidation(() -> admin.addFlight(root, "AR902", "DEL", "DEL", departs, departs.plusMinutes(130), prices));
        expectValidation(() -> admin.addFlight(root, "AR903", "DEL", "HYD", departs, departs.minusMinutes(5), prices));
    }

    @Test
    public void cancellingAFlightRefundsEveryPassengerInFullAtomically() {
        User root = adminUser();
        var flight = flight("AR301", 3);
        Booking a = bookings.book(customer("alice"), request(flight, FareClass.ECONOMY, List.of("A One", "A Two"), List.of(), GOOD_CARD));
        Booking b = bookings.book(customer("bob"), request(flight, FareClass.BUSINESS, List.of("B One"), List.of(), GOOD_CARD));

        AdminService.FlightCancellation result = admin.cancelFlight(root, flight.id());

        assertEquals(2, result.bookingsRefunded());
        assertEquals(a.total().add(b.total()), result.totalRefunded());
        assertEquals("CANCELLED", bookingStatus(a.pnr()));
        assertEquals(0, TestDb.count(db, "SELECT COUNT(*) FROM booking_seats bs JOIN bookings k ON k.booking_id = bs.booking_id "
                + "WHERE k.flight_id = ? AND bs.active = 1", flight.id()));
        assertEquals(0, flights.search("HYD", "BOM", flight.departure().toLocalDate()).size());
        expectValidation(() -> admin.cancelFlight(root, flight.id()));
    }

    @Test
    public void reportsReflectSalesAndRefunds() {
        User root = adminUser();
        var flight = flight("AR101", 4);
        Booking kept = bookings.book(customer("alice"), request(flight, FareClass.ECONOMY, List.of("A One", "A Two"), List.of(), GOOD_CARD));
        User bob = customer("bob");
        Booking dropped = bookings.book(bob, request(flight, FareClass.ECONOMY, List.of("B One"), List.of(), GOOD_CARD));
        var cancellation = bookings.cancel(bob, dropped.pnr()); // 90% refund

        ReportDao.RouteRevenue route = admin.revenueByRoute(root).get(0);
        assertEquals("DEL", route.origin());
        assertEquals(2, route.paidBookings());
        assertEquals(kept.total().add(dropped.total()), route.charged());
        assertEquals(cancellation.refund(), route.refunded());
        assertEquals(route.charged().subtract(route.refunded()), route.net());

        ReportDao.Occupancy top = admin.occupancyReport(root, 7).get(0);
        assertEquals("AR101", top.flightNo());
        assertEquals(2, top.bookedSeats());
        assertEquals(144, top.totalSeats());
    }

    private static void expectValidation(Runnable action) {
        try {
            action.run();
            fail("expected a ValidationException");
        } catch (ValidationException expected) {
            // ok
        }
    }
}
