package com.arms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** A booking with everything a ticket shows. The total is computed from its seats, not stored. */
public record Booking(long id, String pnr, long userId, Flight flight, FareClass fareClass,
                      BookingStatus status, List<Passenger> passengers, BigDecimal total,
                      BigDecimal refundAmount, LocalDateTime bookedAt) {

    public record Passenger(String name, String seatNo, BigDecimal price) {
    }
}
