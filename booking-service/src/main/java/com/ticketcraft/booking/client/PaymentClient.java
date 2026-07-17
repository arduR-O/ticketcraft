package com.ticketcraft.booking.client;

import com.ticketcraft.booking.dto.PaymentRequest;
import com.ticketcraft.booking.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient {

  private final RestClient restClient;

  public PaymentClient(RestClient.Builder restClientBuilder, 
                       @Value("${payment-service.url:http://payment-service}") String paymentServiceUrl) {
    // using RestClient with load balancer via Eureka (payment-service)
    this.restClient = restClientBuilder
        .baseUrl(paymentServiceUrl)
        .build();
  }

  public PaymentResponse processPayment(PaymentRequest request) {
    return restClient.post()
        .uri("/api/v1/payments/process")
        .body(request)
        .retrieve()
        .body(PaymentResponse.class);
  }
}
