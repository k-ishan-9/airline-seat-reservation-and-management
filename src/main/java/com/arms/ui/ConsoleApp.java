package com.arms.ui;

import com.arms.dao.ReportDao;
import com.arms.db.DataAccessException;
import com.arms.model.Booking;
import com.arms.model.CancellationResult;
import com.arms.model.FareClass;
import com.arms.model.FareOption;
import com.arms.model.Flight;
import com.arms.model.FlightOffer;
import com.arms.model.Seat;
import com.arms.model.User;
import com.arms.payment.PaymentMethod;
import com.arms.service.AdminService;
import com.arms.service.ArmsException;
import com.arms.service.AuthService;
import com.arms.service.BookingService;
import com.arms.service.FlightService;
import com.arms.util.SeatLayout;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The text interface. It only talks to services: no SQL here, so the same services could sit
 * behind a REST API or a web page later. Menus are loops, not recursive calls.
 */
public class ConsoleApp {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String LINE = "-".repeat(78);

    private final ConsoleIO io;
    private final Clock clock;
    private final AuthService auth;
    private final FlightService flights;
    private final BookingService bookings;
    private final AdminService admin;
    private User user;

    public ConsoleApp(ConsoleIO io, Clock clock, AuthService auth, FlightService flights,
                      BookingService bookings, AdminService admin) {
        this.io = io;
        this.clock = clock;
        this.auth = auth;
        this.flights = flights;
        this.bookings = bookings;
        this.admin = admin;
    }

    public void run() {
        io.println(LINE);
        io.println("  ARMS - Airline Reservation Management System");
        io.println(LINE);
        boolean running = true;
        while (running) {
            try {
                if (user == null) running = guestMenu();
                else if (user.isAdmin()) adminMenu();
                else customerMenu();
            } catch (ArmsException e) {
                io.println("  ! " + e.getMessage());
            } catch (DataAccessException e) {
                io.println("  ! Database problem: " + e.getMessage());
            } catch (EndOfInputException e) {
                running = false;
            }
        }
        io.println("Goodbye!");
    }

    // ---------- menus ----------

    private boolean guestMenu() {
        io.println();
        io.println("1. Search flights   2. Flights within a budget   3. Log in   4. Register   0. Exit");
        switch (io.askInt("Choose: ", 0, 4)) {
            case 1 -> searchFlights(false);
            case 2 -> budgetSearch();
            case 3 -> login();
            case 4 -> register();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void customerMenu() {
        io.println();
        io.println("Hello " + user.fullName() + ". 1. Search & book   2. Within a budget   3. My bookings   "
                + "4. Cancel a booking   5. Log out");
        switch (io.askInt("Choose: ", 1, 5)) {
            case 1 -> searchFlights(true);
            case 2 -> budgetSearch();
            case 3 -> showMyBookings();
            case 4 -> cancelBooking();
            default -> logout();
        }
    }

    private void adminMenu() {
        io.println();
        io.println("ADMIN  1. Add flight   2. Cancel flight   3. Occupancy report   4. Revenue by route   "
                + "5. Search flights   6. Log out");
        switch (io.askInt("Choose: ", 1, 6)) {
            case 1 -> addFlight();
            case 2 -> cancelFlight();
            case 3 -> occupancyReport();
            case 4 -> revenueReport();
            case 5 -> searchFlights(false);
            default -> logout();
        }
    }

    // ---------- accounts ----------

    private void register() {
        String username = io.askRequired("Choose a username (letters, digits, _): ");
        String fullName = io.askRequired("Full name: ");
        char[] password = io.askPassword("Password (8+ chars, a letter and a digit): ");
        char[] again = io.askPassword("Repeat password: ");
        try {
            if (!Arrays.equals(password, again)) {
                io.println("  ! Passwords don't match.");
                return;
            }
            user = auth.register(username, fullName, password);
            io.println("  Account created. You're logged in as " + user.username() + ".");
        } finally {
            Arrays.fill(password, '\0'); // wipe the password from memory as soon as it's used
            Arrays.fill(again, '\0');
        }
    }

    private void login() {
        String username = io.askRequired("Username: ");
        char[] password = io.askPassword("Password: ");
        try {
            user = auth.login(username, password);
            io.println("  Welcome back, " + user.fullName() + (user.isAdmin() ? " (administrator)" : "") + ".");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void logout() {
        io.println("  Logged out.");
        user = null;
    }

    // ---------- searching and booking ----------

    private void searchFlights(boolean offerBooking) {
        FlightOffer chosen = pickFlight();
        if (chosen == null || !offerBooking) return;
        book(chosen);
    }

    /** Asks for a route and date, prints matching flights, and returns the one picked (or null). */
    private FlightOffer pickFlight() {
        printAirports();
        String origin = io.askRequired("From (code): ");
        String destination = io.askRequired("To (code): ");
        LocalDate today = LocalDate.now(clock);
        LocalDate date = io.askDate("Date [yyyy-MM-dd, today, tomorrow, +N] (Enter = tomorrow): ", today, today.plusDays(1));
        List<FlightOffer> offers = flights.search(origin, destination, date);
        if (offers.isEmpty()) {
            io.println("  No flights on that route on " + date + ".");
            return null;
        }
        printOffers(offers);
        int pick = io.askInt("Pick a flight (0 = back): ", 0, offers.size());
        return pick == 0 ? null : offers.get(pick - 1);
    }

    private void budgetSearch() {
        FareClass fareClass = askClass();
        BigDecimal budget = io.askMoney("Maximum price per seat (Rs): ");
        String origin = io.ask("From (code, Enter = any airport): ");
        List<FlightOffer> offers = flights.withinBudget(fareClass, budget, origin);
        if (offers.isEmpty()) {
            io.println("  Nothing in the next 30 days at or under Rs " + money(budget) + ".");
            return;
        }
        io.println(LINE);
        io.printf(" %-3s %-7s %-9s %-14s %-16s %s%n", "#", "Flight", "Route", "Departs", fareClass.label(), "Seats left");
        for (int i = 0; i < offers.size(); i++) {
            Flight f = offers.get(i).flight();
            FareOption fare = offers.get(i).fare(fareClass);
            io.printf(" %-3d %-7s %-9s %-14s Rs %-13s %d%n", i + 1, f.flightNo(), f.origin() + "-" + f.destination(),
                    f.departure().format(WHEN), money(fare.price()), fare.seatsLeft());
        }
        io.println(LINE);
        if (user != null && !user.isAdmin()) {
            int pick = io.askInt("Book one? Pick a number (0 = no): ", 0, offers.size());
            if (pick > 0) book(offers.get(pick - 1));
        }
    }

    private void book(FlightOffer offer) {
        Flight flight = offer.flight();
        FareClass fareClass = askClass();
        FareOption fare = offer.fare(fareClass);
        if (fare == null || fare.seatsLeft() == 0) {
            io.println("  No " + fareClass.label() + " seats left on " + flight.flightNo() + ".");
            return;
        }
        int maxPassengers = Math.min(BookingService.MAX_PASSENGERS, fare.seatsLeft());
        int count = io.askInt("Number of passengers (1-" + maxPassengers + "): ", 1, maxPassengers);
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= count; i++) names.add(io.askRequired("  Passenger " + i + " full name: "));

        List<Seat> seatMap = flights.seatMap(flight.id(), fareClass);
        printSeatMap(seatMap, fareClass);
        List<String> seats = askSeats(seatMap, count);

        BigDecimal total = fare.price().multiply(BigDecimal.valueOf(count));
        io.println(LINE);
        io.printf(" %s %s-%s, %s, %s x %d = Rs %s%n", flight.flightNo(), flight.origin(), flight.destination(),
                flight.departure().format(WHEN), fareClass.label(), count, money(total));
        io.println(LINE);
        if (!io.confirm("Continue to payment?")) return;

        PaymentMethod payment = askPayment();
        Booking booking = bookings.book(user, new BookingService.BookingRequest(flight.id(), fareClass, names, seats, payment));
        io.println("  Payment approved. Booking confirmed!");
        printTicket(booking);
    }

    /**
     * Early feedback from the seat map just shown, so the customer doesn't type payment details
     * for a seat that's already gone. The booking service still re-checks under a lock, because
     * someone could take the seat between this check and payment.
     */
    private List<String> askSeats(List<Seat> seatMap, int count) {
        while (true) {
            String input = io.ask("Seats, separated by spaces (e.g. 12A 12B), or Enter to auto-assign: ");
            if (input.isBlank()) return List.of();
            List<String> seats = Arrays.stream(input.trim().split("[\\s,]+")).map(String::toUpperCase).toList();
            String problem = null;
            if (seats.size() != count) problem = "Choose " + count + " seat(s), one per passenger.";
            for (String seatNo : seats) {
                Seat seat = seatMap.stream().filter(s -> s.seatNo().equals(seatNo)).findFirst().orElse(null);
                if (problem == null && seat == null) problem = "Seat " + seatNo + " isn't in this cabin.";
                if (problem == null && seat.taken()) problem = "Seat " + seatNo + " is taken. Pick a free one.";
            }
            if (problem == null) return seats;
            io.println("  " + problem);
        }
    }

    private PaymentMethod askPayment() {
        io.println("Pay by: 1. Card   2. UPI    (test card 4111 1111 1111 1111 is approved, 4000 0000 0000 0002 is declined)");
        if (io.askInt("Choose: ", 1, 2) == 1) {
            String number = io.askRequired("Card number: ");
            String holder = io.askRequired("Name on card: ");
            return new PaymentMethod.Card(number, holder);
        }
        return new PaymentMethod.Upi(io.askRequired("UPI ID (e.g. name@okbank): "));
    }

    private void showMyBookings() {
        List<Booking> mine = bookings.myBookings(user);
        if (mine.isEmpty()) {
            io.println("  You have no bookings yet.");
            return;
        }
        mine.forEach(this::printTicket);
    }

    private void cancelBooking() {
        io.println("Refund policy: 72+ hours before departure 90%, 24-72 hours 50%, under 24 hours none.");
        String pnr = io.askRequired("PNR to cancel: ");
        if (!io.confirm("Cancel booking " + pnr.toUpperCase() + "?")) return;
        CancellationResult result = bookings.cancel(user, pnr);
        io.printf("  Booking %s cancelled. Paid Rs %s, refund %d%% = Rs %s to the original payment method.%n",
                result.pnr(), money(result.paid()), result.refundPercent(), money(result.refund()));
    }

    // ---------- admin ----------

    private void addFlight() {
        printAirports();
        String flightNo = io.askRequired("Flight number (e.g. AR901): ");
        String origin = io.askRequired("From (code): ");
        String destination = io.askRequired("To (code): ");
        LocalDateTime departure = io.askDateTime("Departure (yyyy-MM-dd HH:mm): ");
        int minutes = io.askInt("Flight time in minutes: ", 20, 1200);
        Map<FareClass, BigDecimal> prices = new EnumMap<>(FareClass.class);
        for (FareClass fareClass : FareClass.values()) {
            prices.put(fareClass, io.askMoney(fareClass.label() + " price (Rs): "));
        }
        admin.addFlight(user, flightNo, origin, destination, departure, departure.plusMinutes(minutes), prices);
        io.println("  Flight added with " + SeatLayout.standard().size() + " seats.");
    }

    private void cancelFlight() {
        io.println("Find the flight to cancel:");
        FlightOffer offer = pickFlight();
        if (offer == null) return;
        Flight f = offer.flight();
        if (!io.confirm("Cancel " + f.flightNo() + " on " + f.departure().format(WHEN) + " and refund every passenger in full?")) {
            return;
        }
        AdminService.FlightCancellation result = admin.cancelFlight(user, f.id());
        io.printf("  Flight cancelled. %d booking(s) refunded, Rs %s in total.%n",
                result.bookingsRefunded(), money(result.totalRefunded()));
    }

    private void occupancyReport() {
        List<ReportDao.Occupancy> rows = admin.occupancyReport(user, 7);
        io.println(LINE);
        io.println(" Fullest flights in the next 7 days");
        io.printf(" %-7s %-9s %-14s %7s %7s %8s%n", "Flight", "Route", "Departs", "Seats", "Sold", "Load");
        for (ReportDao.Occupancy r : rows) {
            io.printf(" %-7s %-9s %-14s %7d %7d %7s%%%n", r.flightNo(), r.origin() + "-" + r.destination(),
                    r.departure().format(WHEN), r.totalSeats(), r.bookedSeats(), r.loadFactorPercent());
        }
        io.println(LINE);
    }

    private void revenueReport() {
        List<ReportDao.RouteRevenue> rows = admin.revenueByRoute(user);
        io.println(LINE);
        io.printf(" %-9s %9s %15s %15s %15s%n", "Route", "Bookings", "Charged", "Refunded", "Net");
        for (ReportDao.RouteRevenue r : rows) {
            io.printf(" %-9s %9d %15s %15s %15s%n", r.origin() + "-" + r.destination(), r.paidBookings(),
                    money(r.charged()), money(r.refunded()), money(r.net()));
        }
        if (rows.isEmpty()) io.println(" No payments yet.");
        io.println(LINE);
    }

    // ---------- printing ----------

    private void printAirports() {
        io.println("Airports: " + flights.airports().entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(" | ")));
    }

    private void printOffers(List<FlightOffer> offers) {
        io.println(LINE);
        io.printf(" %-3s %-7s %-9s %-13s %-6s %-17s %-17s %s%n",
                "#", "Flight", "Route", "Departs", "Lands", "Economy (left)", "Business (left)", "First (left)");
        for (int i = 0; i < offers.size(); i++) {
            Flight f = offers.get(i).flight();
            io.printf(" %-3d %-7s %-9s %-13s %-6s %-17s %-17s %s%n", i + 1, f.flightNo(), f.origin() + "-" + f.destination(),
                    f.departure().format(WHEN), f.arrival().format(TIME), fareCell(offers.get(i), FareClass.ECONOMY),
                    fareCell(offers.get(i), FareClass.BUSINESS), fareCell(offers.get(i), FareClass.FIRST));
        }
        io.println(LINE);
    }

    private static String fareCell(FlightOffer offer, FareClass fareClass) {
        FareOption fare = offer.fare(fareClass);
        return fare == null ? "-" : money(fare.price()) + " (" + fare.seatsLeft() + ")";
    }

    /** Draws the cabin row by row, with the aisle in the middle and X for taken seats. */
    private void printSeatMap(List<Seat> seats, FareClass fareClass) {
        String letters = SeatLayout.letters(fareClass);
        int half = letters.length() / 2;
        io.println(fareClass.label() + " cabin   [ ] free   [X] taken");
        StringBuilder header = new StringBuilder("      ");
        for (int i = 0; i < letters.length(); i++) {
            header.append(' ').append(letters.charAt(i)).append(' ');
            if (i == half - 1) header.append("   ");
        }
        io.println(header.toString());
        Map<Integer, List<Seat>> rows = seats.stream()
                .collect(Collectors.groupingBy(Seat::row, java.util.TreeMap::new, Collectors.toList()));
        rows.forEach((row, rowSeats) -> {
            StringBuilder line = new StringBuilder(String.format("  %2d  ", row));
            for (int i = 0; i < letters.length(); i++) {
                char letter = letters.charAt(i);
                boolean taken = rowSeats.stream().anyMatch(s -> s.letter() == letter && s.taken());
                line.append(taken ? "[X]" : "[ ]");
                if (i == half - 1) line.append("   ");
            }
            io.println(line.toString());
        });
    }

    private void printTicket(Booking b) {
        Flight f = b.flight();
        io.println(LINE);
        io.printf(" PNR %s   %s   %s %s-%s   %s -> %s%n", b.pnr(), b.status(), f.flightNo(), f.origin(),
                f.destination(), f.departure().format(WHEN), f.arrival().format(TIME));
        for (Booking.Passenger p : b.passengers()) {
            io.printf("   %-4s %-30s %s Rs %s%n", p.seatNo(), p.name(), b.fareClass().label(), money(p.price()));
        }
        io.printf("   Total Rs %s%s%n", money(b.total()),
                b.refundAmount() != null ? "   Refunded Rs " + money(b.refundAmount()) : "");
        io.println(LINE);
    }

    private FareClass askClass() {
        while (true) {
            try {
                return FareClass.parse(io.askRequired("Class [E]conomy, [B]usiness, [F]irst: "));
            } catch (IllegalArgumentException e) {
                io.println("  Type E, B or F.");
            }
        }
    }

    private static String money(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }
}
