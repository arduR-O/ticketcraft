package com.ticketcraft.catalog.grpc;

import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.repository.SeatRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceGrpcImplTests {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private StreamObserver<SeatCheckResponse> responseObserver;

    @InjectMocks
    private SeatServiceGrpcImpl seatServiceGrpc;

    @Test
    void checkSeats_whenAllSeatsAvailable_shouldReturnTrue() {
        // Arrange
        Event event = Event.builder().id(1L).build();
        Seat seat1 = Seat.builder()
                .id(101L)
                .seatNumber("A-1")
                .rowNumber("Row-1")
                .category("VIP")
                .status("AVAILABLE")
                .price(new BigDecimal("100.00"))
                .event(event)
                .build();

        when(seatRepository.findAllById(List.of(101L))).thenReturn(List.of(seat1));

        SeatCheckRequest request = SeatCheckRequest.newBuilder()
                .addSeatIds(101L)
                .setEventId(1L)
                .build();

        // Act
        seatServiceGrpc.checkSeats(request, responseObserver);

        // Assert
        ArgumentCaptor<SeatCheckResponse> responseCaptor = ArgumentCaptor.forClass(SeatCheckResponse.class);
        verify(responseObserver, times(1)).onNext(responseCaptor.capture());
        verify(responseObserver, times(1)).onCompleted();

        SeatCheckResponse response = responseCaptor.getValue();
        assertThat(response.getAllAvailable()).isTrue();
        assertThat(response.getSeatsList()).hasSize(1);
        assertThat(response.getSeats(0).getSeatNumber()).isEqualTo("A-1");
        assertThat(response.getSeats(0).getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void checkSeats_whenASeatIsSold_shouldReturnFalse() {
        // Arrange
        Event event = Event.builder().id(1L).build();
        Seat seat1 = Seat.builder()
                .id(101L)
                .seatNumber("A-1")
                .rowNumber("Row-1")
                .category("VIP")
                .status("SOLD")
                .price(new BigDecimal("100.00"))
                .event(event)
                .build();

        when(seatRepository.findAllById(List.of(101L))).thenReturn(List.of(seat1));

        SeatCheckRequest request = SeatCheckRequest.newBuilder()
                .addSeatIds(101L)
                .setEventId(1L)
                .build();

        // Act
        seatServiceGrpc.checkSeats(request, responseObserver);

        // Assert
        ArgumentCaptor<SeatCheckResponse> responseCaptor = ArgumentCaptor.forClass(SeatCheckResponse.class);
        verify(responseObserver).onNext(responseCaptor.capture());
        verify(responseObserver).onCompleted();

        SeatCheckResponse response = responseCaptor.getValue();
        assertThat(response.getAllAvailable()).isFalse();
    }
}
