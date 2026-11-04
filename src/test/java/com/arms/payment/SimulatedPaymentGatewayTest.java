package com.arms.payment;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
    private final BigDecimal amount = new BigDecimal("5200.00");

    @Test
    public void approvesAValidCard() {
        PaymentResult result = gateway.charge(amount, new PaymentMethod.Card("4111 1111 1111 1111", "A"), "PNR123");
        assertTrue(result.approved());
        assertTrue(result.reference().startsWith("TXN"));
    }

    @Test
    public void luhnCatchesATypo() {
        assertTrue(SimulatedPaymentGateway.passesLuhn("4111111111111111"));
        assertFalse(SimulatedPaymentGateway.passesLuhn("4111111111111112"));
        assertFalse(gateway.charge(amount, new PaymentMethod.Card("4111111111111112", "A"), "P").approved());
    }

    @Test
    public void bankDeclineTestCard() {
        PaymentResult result = gateway.charge(amount, new PaymentMethod.Card("4000000000000002", "A"), "P");
        assertFalse(result.approved());
        assertEquals("Card declined by the bank", result.message());
    }

    @Test
    public void upiFormatIsChecked() {
        assertTrue(gateway.charge(amount, new PaymentMethod.Upi("kishan@okhdfc"), "P").approved());
        assertFalse(gateway.charge(amount, new PaymentMethod.Upi("not-an-id"), "P").approved());
    }

    @Test
    public void storedLabelsNeverContainTheCardNumber() {
        assertEquals("CARD ****1111", new PaymentMethod.Card("4111 1111 1111 1111", "A").masked());
        assertEquals("UPI k***@okhdfc", new PaymentMethod.Upi("kishan@okhdfc").masked());
    }
}
