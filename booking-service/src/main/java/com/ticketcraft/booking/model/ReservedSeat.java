package com.ticketcraft.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "reserved_seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservedSeat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id", nullable = false)
  @ToString.Exclude // Prevent infinite recursion in toString
  private Booking booking;

  @Column(name = "seat_id", nullable = false)
  private Long seatId;

  @Column(name = "seat_number", nullable = false)
  private String seatNumber;

  @Column(name = "row_number", nullable = false)
  private String rowNumber;

  @Column(nullable = false)
  private String section;

  @Column(nullable = false)
  private BigDecimal price;
}
