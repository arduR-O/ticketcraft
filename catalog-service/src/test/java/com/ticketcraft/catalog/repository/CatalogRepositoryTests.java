package com.ticketcraft.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketcraft.catalog.model.Artist;
import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.model.SeatCategory;
import com.ticketcraft.catalog.model.SeatStatus;
import com.ticketcraft.catalog.model.Venue;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CatalogRepositoryTests {

  @Autowired private ArtistRepository artistRepository;
  @Autowired private VenueRepository venueRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private SeatRepository seatRepository;

  @Test
  void shouldSaveAndRetrieveArtist() {
    Artist artist =
        Artist.builder().name("Queen").genre("Rock").description("British rock band.").build();
    Artist saved = artistRepository.save(artist);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getName()).isEqualTo("Queen");
  }

  @Test
  void shouldSaveAndRetrieveVenue() {
    Venue venue =
        Venue.builder()
            .name("Wembley Stadium")
            .location("London, UK")
            .capacity(90000)
            .latitude(51.5560)
            .longitude(-0.2796)
            .build();
    Venue saved = venueRepository.save(venue);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getLatitude()).isEqualTo(51.5560);
    assertThat(saved.getLongitude()).isEqualTo(-0.2796);
  }

  @Test
  void shouldSaveAndRetrieveEvent() {
    Artist artist = artistRepository.save(Artist.builder().name("Queen").build());
    Venue venue =
        venueRepository.save(
            Venue.builder().name("Wembley").location("UK").capacity(90000).build());

    Event event =
        Event.builder()
            .title("Queen Live")
            .description("Live Concert")
            .date(LocalDateTime.now().plusDays(10))
            .artist(artist)
            .venue(venue)
            .build();

    Event saved = eventRepository.save(event);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getArtist().getId()).isEqualTo(artist.getId());
    assertThat(saved.getVenue().getId()).isEqualTo(venue.getId());
  }

  @ParameterizedTest
  @CsvSource({"VIP, AVAILABLE", "STANDARD, RESERVED", "BALCONY, SOLD"})
  void shouldSaveSeatWithVariousCategoriesAndStatuses(SeatCategory category, SeatStatus status) {

    Artist artist = artistRepository.save(Artist.builder().name("Queen").build());
    Venue venue =
        venueRepository.save(
            Venue.builder().name("Wembley").location("UK").capacity(90000).build());
    Event event =
        eventRepository.save(
            Event.builder()
                .title("Concert")
                .date(LocalDateTime.now())
                .artist(artist)
                .venue(venue)
                .build());

    Seat seat =
        Seat.builder()
            .seatNumber("A-101")
            .rowNumber("Row-10")
            .section("Section 101")
            .xCoordinate(10)
            .yCoordinate(20)
            .category(category)
            .status(status)
            .price(new BigDecimal("150.00"))
            .event(event)
            .build();

    Seat saved = seatRepository.save(seat);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCategory()).isEqualTo(category);
    assertThat(saved.getStatus()).isEqualTo(status);
  }

  @Test
  void shouldFindSeatsByEventId() {
    Artist artist = artistRepository.save(Artist.builder().name("Queen").build());
    Venue venue =
        venueRepository.save(
            Venue.builder().name("Wembley").location("UK").capacity(90000).build());
    Event event =
        eventRepository.save(
            Event.builder()
                .title("Concert")
                .date(LocalDateTime.now())
                .artist(artist)
                .venue(venue)
                .build());

    Seat seat1 =
        Seat.builder()
            .seatNumber("A-101")
            .rowNumber("Row-10")
            .section("Section 101")
            .xCoordinate(10)
            .yCoordinate(20)
            .category(SeatCategory.VIP)
            .status(SeatStatus.AVAILABLE)
            .price(new BigDecimal("150.00"))
            .event(event)
            .build();

    Seat seat2 =
        Seat.builder()
            .seatNumber("A-102")
            .rowNumber("Row-10")
            .section("Section 101")
            .xCoordinate(30)
            .yCoordinate(20)
            .category(SeatCategory.STANDARD)
            .status(SeatStatus.AVAILABLE)
            .price(new BigDecimal("80.00"))
            .event(event)
            .build();

    seatRepository.saveAll(List.of(seat1, seat2));

    List<Seat> seats = seatRepository.findByEventId(event.getId());
    assertThat(seats).hasSize(2);
    assertThat(seats).extracting(Seat::getSeatNumber).containsExactlyInAnyOrder("A-101", "A-102");
  }
}
