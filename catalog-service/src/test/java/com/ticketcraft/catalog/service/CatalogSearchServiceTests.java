package com.ticketcraft.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.ticketcraft.catalog.model.Event;
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

import com.ticketcraft.catalog.dto.EventResponse;
import com.ticketcraft.catalog.model.Artist;
import com.ticketcraft.catalog.model.Venue;

@ExtendWith(MockitoExtension.class)
class CatalogSearchServiceTests {

  @Mock private EventRepository eventRepository;
  @Mock private SeatRepository seatRepository;
  @InjectMocks private CatalogSearchService catalogSearchService;

  private Event testEvent;
  private Pageable pageable;

  @BeforeEach
  void setUp() {
    testEvent =
        Event.builder()
            .id(1L)
            .title("Queen Live at Wembley")
            .description("Magic Tour")
            .artist(Artist.builder().name("Queen").build())
            .venue(Venue.builder().name("Wembley Stadium").location("London").build())
            .build();
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
    when(eventRepository.searchEvents("Queen", pageable)).thenReturn(List.of(testEvent));

    List<EventResponse> results = catalogSearchService.searchEvents("Queen", pageable);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).title()).isEqualTo("Queen Live at Wembley");
    assertThat(results.get(0).artistName()).isEqualTo("Queen");
    verify(eventRepository, times(1)).searchEvents("Queen", pageable);
    verify(eventRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void searchEvents_shouldPassRawQueryToRepository() {
    when(eventRepository.searchEvents("Queen & Wembley!", pageable)).thenReturn(List.of(testEvent));

    List<EventResponse> results = catalogSearchService.searchEvents("Queen & Wembley!", pageable);

    assertThat(results).hasSize(1);
    verify(eventRepository, times(1)).searchEvents("Queen & Wembley!", pageable);
  }
}
