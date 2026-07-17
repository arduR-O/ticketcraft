package com.ticketcraft.booking.grpc;

import com.ticketcraft.catalog.grpc.SeatCheckRequest;
import com.ticketcraft.catalog.grpc.SeatCheckResponse;
import com.ticketcraft.catalog.grpc.SeatServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CatalogGrpcClient {

  @GrpcClient("catalog-service")
  private SeatServiceGrpc.SeatServiceBlockingStub seatServiceStub;

  /**
   * Calls catalog-service over gRPC to check if the given seats are valid and available for the event.
   *
   * @param eventId The event ID.
   * @param seatIds The list of seat IDs to check.
   * @return The response containing seat details and availability status.
   */
  @CircuitBreaker(name = "catalogGrpcCircuitBreaker", fallbackMethod = "checkSeatsFallback")
  public SeatCheckResponse checkSeats(Long eventId, List<Long> seatIds) {
    SeatCheckRequest request =
        SeatCheckRequest.newBuilder().setEventId(eventId).addAllSeatIds(seatIds).build();
    return seatServiceStub.checkSeats(request);
  }

  /**
   * Fallback method called when CircuitBreaker opens or an exception occurs during the gRPC call.
   *
   * @param eventId The event ID.
   * @param seatIds The list of seat IDs.
   * @param t       The exception that triggered the fallback.
   * @return A response indicating seats are unavailable due to fallback.
   */
  @SuppressWarnings("unused")
  public SeatCheckResponse checkSeatsFallback(Long eventId, List<Long> seatIds, Throwable t) {
    log.error("Catalog gRPC call failed for eventId: {}, seatIds: {}. Circuit breaker fallback triggered.", eventId, seatIds, t);
    return SeatCheckResponse.newBuilder()
        .setAllAvailable(false)
        .build();
  }
}
