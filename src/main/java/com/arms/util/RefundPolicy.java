package com.arms.util;

import com.arms.service.ValidationException;

import java.time.Duration;

/**
 * Cancellation refunds, based on how long before departure the booking is cancelled:
 * 72 hours or more: 90% (a 10% fee); 24 to 72 hours: 50%; under 24 hours: no refund.
 * A pure function, so every boundary can be unit-tested without a database.
 */
public final class RefundPolicy {

    private RefundPolicy() {
    }

    public static int refundPercent(Duration timeBeforeDeparture) {
        if (timeBeforeDeparture.isNegative() || timeBeforeDeparture.isZero()) {
            throw new ValidationException("This flight has already departed and can't be cancelled");
        }
        if (timeBeforeDeparture.compareTo(Duration.ofHours(72)) >= 0) return 90;
        if (timeBeforeDeparture.compareTo(Duration.ofHours(24)) >= 0) return 50;
        return 0;
    }
}
