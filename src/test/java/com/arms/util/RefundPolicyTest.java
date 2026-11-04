package com.arms.util;

import com.arms.service.ValidationException;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class RefundPolicyTest {

    @Test
    public void ninetyPercentAtSeventyTwoHoursOrMore() {
        assertEquals(90, RefundPolicy.refundPercent(Duration.ofHours(72)));
        assertEquals(90, RefundPolicy.refundPercent(Duration.ofDays(10)));
    }

    @Test
    public void fiftyPercentBetweenTwentyFourAndSeventyTwoHours() {
        assertEquals(50, RefundPolicy.refundPercent(Duration.ofHours(24)));
        assertEquals(50, RefundPolicy.refundPercent(Duration.ofHours(72).minusMinutes(1)));
    }

    @Test
    public void nothingUnderTwentyFourHours() {
        assertEquals(0, RefundPolicy.refundPercent(Duration.ofHours(24).minusMinutes(1)));
        assertEquals(0, RefundPolicy.refundPercent(Duration.ofMinutes(5)));
    }

    @Test(expected = ValidationException.class)
    public void departedFlightsCantBeCancelled() {
        RefundPolicy.refundPercent(Duration.ofMinutes(-1));
    }
}
