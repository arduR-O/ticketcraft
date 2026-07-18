package com.ticketcraft.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.ticketcraft.catalog.dto.EventResponse;
import com.ticketcraft.catalog.model.Artist;
import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Venue;
import com.ticketcraft.catalog.repository.EventRepository;
import com.ticketcraft.catalog.repository.SeatRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CatalogSearchServiceTests {

  @Mock private EventRepository eventRepository;
  @Mock private SeatRepository seatRepository;
  @InjectMocks private CatalogSearchService catalogSearchService;

  private com.ticketcraft.catalog.repository.EventSummaryProjection testProjection;
  private Pageable pageable;

  @BeforeEach
  void setUp() {
    testProjection = mock(com.ticketcraft.catalog.repository.EventSummaryProjection.class);
    lenient().when(testProjection.getId()).thenReturn(1L);
    lenient().when(testProjection.getTitle()).thenReturn("Queen Live at Wembley");
    lenient().when(testProjection.getDescription()).thenReturn("Magic Tour");
    lenient().when(testProjection.getArtistName()).thenReturn("Queen");
    lenient().when(testProjection.getVenueName()).thenReturn("Wembley Stadium");
    lenient().when(testProjection.getVenueLocation()).thenReturn("London");

    pageable = PageRequest.of(0, 20);
  }

  @Test
  void searchEvents_withEmptyQuery_shouldReturnEmptyList() {
    List<EventResponse> results = catalogSearchService.searchEvents("", pageable);

    assertThat(results).isEmpty();
    verify(eventRepository, never()).findAll(any(Pageable.class));
    verify(eventRepository, never()).searchEvents(anyString(), any(Pageable.class));
  }

  @Test
  void searchEvents_withNullQuery_shouldReturnEmptyList() {
    List<EventResponse> results = catalogSearchService.searchEvents(null, pageable);

    assertThat(results).isEmpty();
    verify(eventRepository, never()).findAll(any(Pageable.class));
    verify(eventRepository, never()).searchEvents(anyString(), any(Pageable.class));
  }

  @Test
  void searchEvents_withQuery_shouldCallSearchRepository() {
    when(eventRepository.searchEvents("Queen", pageable)).thenReturn(List.of(testProjection));

    List<EventResponse> results = catalogSearchService.searchEvents("Queen", pageable);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).title()).isEqualTo("Queen Live at Wembley");
    assertThat(results.get(0).artistName()).isEqualTo("Queen");
    verify(eventRepository, times(1)).searchEvents("Queen", pageable);
    verify(eventRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void searchEvents_shouldPassRawQueryToRepository() {
    when(eventRepository.searchEvents("Queen & Wembley!", pageable)).thenReturn(List.of(testProjection));

    List<EventResponse> results = catalogSearchService.searchEvents("Queen & Wembley!", pageable);

    assertThat(results).hasSize(1);
    verify(eventRepository, times(1)).searchEvents("Queen & Wembley!", pageable);
  }

  @Test
  void getEventDetail_shouldReturnAggregatedDetails() {
    Artist artist = Artist.builder().name("Queen").build();
    Venue venue =
        Venue.builder()
            .name("Wembley Stadium")
            .location("London")
            .latitude(51.556)
            .longitude(-0.2795)
            .build();
    Event event =
        Event.builder()
            .id(1001L)
            .title("Queen Live at Wembley")
            .description("Magic Tour")
            .artist(artist)
            .venue(venue)
            .build();

    com.ticketcraft.catalog.model.Seat seat1 =
        com.ticketcraft.catalog.model.Seat.builder()
            .id(104L)
            .seatNumber("4")
            .rowNumber("A")
            .section("VIP")
            .category(com.ticketcraft.catalog.model.SeatCategory.VIP)
            .status(com.ticketcraft.catalog.model.SeatStatus.AVAILABLE)
            .price(java.math.BigDecimal.valueOf(150.00))
            .event(event)
            .build();

    com.ticketcraft.catalog.model.Seat seat2 =
        com.ticketcraft.catalog.model.Seat.builder()
            .id(105L)
            .seatNumber("5")
            .rowNumber("A")
            .section("VIP")
            .category(com.ticketcraft.catalog.model.SeatCategory.VIP)
            .status(com.ticketcraft.catalog.model.SeatStatus.LOCKED)
            .price(java.math.BigDecimal.valueOf(150.00))
            .event(event)
            .build();

    when(eventRepository.findById(1001L)).thenReturn(java.util.Optional.of(event));
    when(seatRepository.findByEventId(1001L)).thenReturn(List.of(seat1, seat2));

    com.ticketcraft.catalog.dto.EventDetailResponse details =
        catalogSearchService.getEventDetail(1001L);

    assertThat(details.id()).isEqualTo(1001L);
    assertThat(details.title()).isEqualTo("Queen Live at Wembley");
    assertThat(details.totalSeats()).isEqualTo(2);
    assertThat(details.availableSeats()).isEqualTo(1);
    assertThat(details.venueLatitude()).isEqualTo(51.556);
    assertThat(details.pricingTiers()).containsEntry("VIP", java.math.BigDecimal.valueOf(150.00));
  }
}
