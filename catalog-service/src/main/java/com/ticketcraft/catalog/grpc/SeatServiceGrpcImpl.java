package com.ticketcraft.catalog.grpc;

import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.model.SeatStatus;
import com.ticketcraft.catalog.repository.SeatRepository;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

@GrpcService
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SeatServiceGrpcImpl extends SeatServiceGrpc.SeatServiceImplBase {

  private final SeatRepository seatRepository;

  @Override
  public void checkSeats(
      SeatCheckRequest request, StreamObserver<SeatCheckResponse> responseObserver) {

    List<Long> requestedSeatIds = request.getSeatIdsList();
    long eventId = request.getEventId();

    log.info("gRPC Request - Checking seats: {} and event: {}", requestedSeatIds, eventId);

    try {
      List<Seat> seats = seatRepository.findAllByIdWithEvent(requestedSeatIds);

      // Validate matching requested criteria:
      // - Did we find all the requested seats?
      // - Do all seats belong to the requested event?
      // - Are all seats currently "AVAILABLE"?
      boolean allFound = seats.size() == requestedSeatIds.size();
      boolean allMatchEvent = seats.stream().allMatch(seat -> seat.getEvent().getId() == eventId);
      boolean allAvailable =
          seats.stream().allMatch(seat -> seat.getStatus() == SeatStatus.AVAILABLE);

      boolean allAvailableStatus = allFound && allMatchEvent && allAvailable;

      List<SeatInfo> seatInfoList =
          seats.stream()
              .map(
                  seat ->
                      SeatInfo.newBuilder()
                          .setSeatId(seat.getId())
                          .setSeatNumber(seat.getSeatNumber())
                          .setRowNumber(seat.getRowNumber())
                          .setStatus(seat.getStatus().name())
                          .setPrice(seat.getPrice().toString())
                          .setCategory(seat.getCategory().name())
                          .setSection(seat.getSection())
                          .setXCoordinate(seat.getXCoordinate() != null ? seat.getXCoordinate() : 0)
                          .setYCoordinate(seat.getYCoordinate() != null ? seat.getYCoordinate() : 0)
                          .build())
              .collect(Collectors.toList());

      SeatCheckResponse response =
          SeatCheckResponse.newBuilder()
              .setAllAvailable(allAvailableStatus)
              .addAllSeats(seatInfoList)
              .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception e) {
      log.error("Failed to execute gRPC seat check", e);
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Internal error checking seats: " + e.getMessage())
              .asRuntimeException());
    }
  }
}
