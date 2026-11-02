package com.arms.model;

import java.math.BigDecimal;

/** One bookable class on one flight: its price and how many seats are still free. */
public record FareOption(FareClass fareClass, BigDecimal price, int seatsLeft) {
}
