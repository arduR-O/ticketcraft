package com.ticketcraft.booking.service;

import org.springframework.stereotype.Service;

@Service
public class PaymentTokenizer {

  /**
   * Tokenizes a raw credit card number into a payment token.
   * In a real system, this would call a PCI-compliant vault or tokenization service.
   * For this implementation, we use simple pattern matching to return test tokens.
   *
   * @param cardNumber The raw credit card number.
   * @return A tokenized representation (e.g., "tok_visa_4242").
   */
  public String tokenize(String cardNumber) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      return "tok_invalid";
    }

    if (cardNumber.startsWith("4242")) {
      return "tok_visa_4242";
    } else if (cardNumber.startsWith("4000")) {
      return "tok_declined";
    } else if (cardNumber.startsWith("5555")) {
      return "tok_mc_5555";
    } else {
      // Default generic token
      return "tok_" + cardNumber.substring(0, Math.min(4, cardNumber.length())) + "_generic";
    }
  }
}
