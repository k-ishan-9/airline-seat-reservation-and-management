package com.arms.model;

/**
 * Life cycle: HELD (seats reserved while paying) -> CONFIRMED -> CANCELLED.
 * A hold can also end as PAYMENT_FAILED or EXPIRED, which releases its seats.
 */
public enum BookingStatus { HELD, CONFIRMED, CANCELLED, PAYMENT_FAILED, EXPIRED }
