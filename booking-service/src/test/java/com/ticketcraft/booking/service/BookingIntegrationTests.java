package com.ticketcraft.booking.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketcraft.booking.client.PaymentClient;
import com.ticketcraft.booking.dto.BookingRequest;
import com.ticketcraft.booking.dto.CheckoutRequest;
import com.ticketcraft.booking.dto.PaymentRequest;
import com.ticketcraft.booking.dto.PaymentResponse;
import com.ticketcraft.booking.grpc.CatalogGrpcClient;
import com.ticketcraft.booking.kafka.SeatStatusProducer;
import com.ticketcraft.booking.model.Booking;
import com.ticketcraft.booking.repository.BookingRepository;
import com.ticketcraft.catalog.grpc.SeatCheckResponse;
import com.ticketcraft.catalog.grpc.SeatInfo;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BookingIntegrationTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private BookingRepository bookingRepository;
  @MockBean private com.ticketcraft.booking.repository.ReservedSeatRepository reservedSeatRepository;
  @MockBean private org.redisson.api.RedissonClient redissonClient;

  // Mocking infrastructure dependencies
  @MockBean private SeatLockService seatLockService;
  @MockBean private CatalogGrpcClient catalogGrpcClient;
  @MockBean private SeatStatusProducer seatStatusProducer;
  @MockBean private PaymentClient paymentClient;
  @MockBean private KafkaTemplate<String, Object> kafkaTemplate; // Excluded autoconfig, but just in case
  
  @BeforeEach
  void setUp() {
    // bookingRepository is mocked, no need to delete all
  }

  @Test
  void testReserveSeatsFlow() throws Exception {
    BookingRequest request = new BookingRequest();
    request.setEventId(1001L);
    request.setSeatIds(Arrays.asList(104L, 105L));

    when(seatLockService.acquireLocks(anyList())).thenReturn(true);

    SeatInfo seat1 = SeatInfo.newBuilder().setSeatId(104L).setPrice("150.00").build();
    SeatInfo seat2 = SeatInfo.newBuilder().setSeatId(105L).setPrice("150.00").build();
    SeatCheckResponse grpcResponse = SeatCheckResponse.newBuilder()
        .setAllAvailable(true)
        .addSeats(seat1)
        .addSeats(seat2)
        .build();

    when(catalogGrpcClient.checkSeats(anyLong(), anyList())).thenReturn(grpcResponse);

    when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
        Booking b = invocation.getArgument(0);
        b.setId(UUID.randomUUID());
        return b;
    });

    mockMvc.perform(post("/api/bookings")
            .header("X-User-Id", "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.totalPrice").value(300.00))
        .andExpect(jsonPath("$.bookingId").exists());
  }

  @Test
  void testCheckoutFlow() throws Exception {
    // 1. Create a pending booking first
    BookingRequest request = new BookingRequest();
    request.setEventId(1001L);
    request.setSeatIds(Arrays.asList(104L, 105L));

    when(seatLockService.acquireLocks(anyList())).thenReturn(true);
    SeatCheckResponse grpcResponse = SeatCheckResponse.newBuilder()
        .setAllAvailable(true)
        .addSeats(SeatInfo.newBuilder().setSeatId(104L).setPrice("150.00").build())
        .addSeats(SeatInfo.newBuilder().setSeatId(105L).setPrice("150.00").build())
        .build();
    when(catalogGrpcClient.checkSeats(anyLong(), anyList())).thenReturn(grpcResponse);

    when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
        Booking b = invocation.getArgument(0);
        b.setId(UUID.randomUUID());
        return b;
    });

    MvcResult result = mockMvc.perform(post("/api/bookings")
            .header("X-User-Id", "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andReturn();

    String responseString = result.getResponse().getContentAsString();
    // Assuming we can just get the ID from the string, or we parse it:
    String bookingIdStr = objectMapper.readTree(responseString).get("bookingId").asText();
    UUID bookingId = UUID.fromString(bookingIdStr);

    // 2. Perform checkout
    CheckoutRequest checkoutRequest = new CheckoutRequest();
    checkoutRequest.setCardNumber("4242424242424242");
    
    Booking mockedBooking = Booking.builder()
        .id(bookingId)
        .eventId(1001L)
        .userId("alice")
        .status(com.ticketcraft.booking.model.BookingStatus.PENDING)
        .totalPrice(new java.math.BigDecimal("300.00"))
        .expiresAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10))
        .build();
    mockedBooking.addReservedSeat(com.ticketcraft.booking.model.ReservedSeat.builder().seatId(104L).price(new java.math.BigDecimal("150.00")).build());
    mockedBooking.addReservedSeat(com.ticketcraft.booking.model.ReservedSeat.builder().seatId(105L).price(new java.math.BigDecimal("150.00")).build());
    
    when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(mockedBooking));

    when(paymentClient.processPayment(any(PaymentRequest.class)))
        .thenReturn(PaymentResponse.builder().status("SUCCESS").build());

    mockMvc.perform(post("/api/bookings/" + bookingId + "/checkout")
            .header("X-User-Id", "alice")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(checkoutRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }
}
