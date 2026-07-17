package com.ticketcraft.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketcraft.booking.client.PaymentClient;
import com.ticketcraft.booking.dto.BookingRequest;
import com.ticketcraft.booking.dto.BookingResponse;
import com.ticketcraft.booking.dto.CheckoutRequest;
import com.ticketcraft.booking.dto.PaymentRequest;
import com.ticketcraft.booking.dto.PaymentResponse;
import com.ticketcraft.booking.event.SeatStatusChangedEvent;
import com.ticketcraft.booking.exception.InvalidBookingRequestException;
import com.ticketcraft.booking.exception.SeatUnavailableException;
import com.ticketcraft.booking.grpc.CatalogGrpcClient;
import com.ticketcraft.booking.kafka.SeatStatusProducer;
import com.ticketcraft.booking.model.Booking;
import com.ticketcraft.booking.model.BookingStatus;
import com.ticketcraft.booking.model.ReservedSeat;
import com.ticketcraft.booking.repository.BookingRepository;
import com.ticketcraft.catalog.grpc.SeatCheckResponse;
import com.ticketcraft.catalog.grpc.SeatInfo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {

  @Mock private BookingRepository bookingRepository;
  @Mock private SeatLockService seatLockService;
  @Mock private CatalogGrpcClient catalogGrpcClient;
  @Mock private SeatStatusProducer seatStatusProducer;
  @Mock private PaymentClient paymentClient;
  @Mock private PaymentTokenizer paymentTokenizer;

  @InjectMocks private BookingService bookingService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(bookingService, "cartExpiryMinutes", 10);
    ReflectionTestUtils.setField(bookingService, "maxSeatsPerBooking", 6);
  }

  @Test
  void testReserveSeats_Success() {
    BookingRequest request = new BookingRequest();
    request.setEventId(1001L);
    request.setSeatIds(Arrays.asList(105L, 104L));

    when(seatLockService.acquireLocks(Arrays.asList(104L, 105L))).thenReturn(true);

    SeatInfo seat1 = SeatInfo.newBuilder().setSeatId(104L).setPrice("150.00").build();
    SeatInfo seat2 = SeatInfo.newBuilder().setSeatId(105L).setPrice("150.00").build();
    SeatCheckResponse grpcResponse = SeatCheckResponse.newBuilder()
        .setAllAvailable(true)
        .addSeats(seat1)
        .addSeats(seat2)
        .build();

    when(catalogGrpcClient.checkSeats(1001L, Arrays.asList(104L, 105L))).thenReturn(grpcResponse);

    Booking savedBooking = Booking.builder()
        .id(UUID.randomUUID())
        .eventId(1001L)
        .userId("alice")
        .status(BookingStatus.PENDING)
        .totalPrice(new BigDecimal("300.00"))
        .createdAt(LocalDateTime.now(ZoneOffset.UTC))
        .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10))
        .build();

    when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

    BookingResponse response = bookingService.reserveSeats(request, "alice");

    assertNotNull(response);
    assertEquals(BookingStatus.PENDING, response.getStatus());
    verify(seatStatusProducer, times(1)).sendSeatStatusChanged(any(SeatStatusChangedEvent.class));
  }

  @Test
  void testReserveSeats_LockFails() {
    BookingRequest request = new BookingRequest();
    request.setEventId(1001L);
    request.setSeatIds(Arrays.asList(105L, 104L));

    when(seatLockService.acquireLocks(anyList())).thenReturn(false);

    assertThrows(SeatUnavailableException.class, () -> {
      bookingService.reserveSeats(request, "alice");
    });
    
    verify(catalogGrpcClient, never()).checkSeats(anyLong(), anyList());
  }

  @Test
  void testInitiateCheckout_Success() {
    UUID bookingId = UUID.randomUUID();
    CheckoutRequest request = new CheckoutRequest();
    request.setCardNumber("4242424242424242");

    Booking booking = Booking.builder()
        .id(bookingId)
        .eventId(1001L)
        .userId("alice")
        .status(BookingStatus.PENDING)
        .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))
        .totalPrice(new BigDecimal("150.00"))
        .build();
    
    ReservedSeat rs = new ReservedSeat();
    rs.setSeatId(104L);
    booking.addReservedSeat(rs);

    when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
    when(paymentTokenizer.tokenize(anyString())).thenReturn("tok_visa_4242");

    PaymentResponse paymentResponse = PaymentResponse.builder()
        .status("SUCCESS")
        .build();
    when(paymentClient.processPayment(any(PaymentRequest.class))).thenReturn(paymentResponse);
    when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

    BookingResponse response = bookingService.initiateCheckout(bookingId, request, "alice");

    assertEquals(BookingStatus.CONFIRMED, response.getStatus());
    verify(seatLockService, times(1)).releaseLocksByIds(Arrays.asList(104L));
    verify(seatStatusProducer, times(1)).sendSeatStatusChanged(any(SeatStatusChangedEvent.class));
  }
}
