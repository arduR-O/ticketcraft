package com.ticketcraft.payment.service;

import com.ticketcraft.payment.dto.PaymentRequest;
import com.ticketcraft.payment.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentProcessingService {

    public PaymentResponse process(PaymentRequest request) {
        String token = request.getPaymentToken();
        
        if (token == null) {
            return new PaymentResponse("FAILED", null, "Invalid token format");
        }
        
        if (token.equals("tok_visa_4242") || token.equals("tok_mc_5555")) {
            String transactionId = "txn_tc_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
            return new PaymentResponse("SUCCESS", transactionId, null);
        } else if (token.equals("tok_declined")) {
            return new PaymentResponse("FAILED", null, "Card declined by issuer");
        } else if (token.startsWith("tok_")) {
            return new PaymentResponse("FAILED", null, "Unknown card type");
        } else {
            return new PaymentResponse("FAILED", null, "Invalid token format");
        }
    }
}
