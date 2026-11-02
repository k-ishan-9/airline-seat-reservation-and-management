package com.arms.model;

import java.util.List;

/** A search result: a flight with the price and availability of each class. */
public record FlightOffer(Flight flight, List<FareOption> fares) {
    public FareOption fare(FareClass fareClass) {
        return fares.stream().filter(f -> f.fareClass() == fareClass).findFirst().orElse(null);
    }
}
