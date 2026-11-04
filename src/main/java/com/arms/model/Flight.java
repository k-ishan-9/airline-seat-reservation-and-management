package com.arms.model;

import java.time.LocalDateTime;

public record Flight(long id, String flightNo, String origin, String destination,
                     LocalDateTime departure, LocalDateTime arrival, String status) {
    public boolean isScheduled() {
        return "SCHEDULED".equals(status);
    }
}
