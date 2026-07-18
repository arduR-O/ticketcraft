package com.ticketcraft.payment.dto;

public class PaymentResponse {

    private String status;
    private String transactionId;
    private String failureReason;

    public PaymentResponse() {
    }

    public PaymentResponse(String status, String transactionId, String failureReason) {
        this.status = status;
        this.transactionId = transactionId;
        this.failureReason = failureReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
