package com.ticketcraft.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentRequest {

    private UUID bookingId;
    private BigDecimal amount;
    private String paymentToken;

    public PaymentRequest() {
    }

    public PaymentRequest(UUID bookingId, BigDecimal amount, String paymentToken) {
        this.bookingId = bookingId;
        this.amount = amount;
        this.paymentToken = paymentToken;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }
}
