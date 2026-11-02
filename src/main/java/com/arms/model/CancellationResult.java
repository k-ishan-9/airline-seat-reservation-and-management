package com.arms.model;

import java.math.BigDecimal;

public record CancellationResult(String pnr, BigDecimal paid, int refundPercent, BigDecimal refund) {
}
