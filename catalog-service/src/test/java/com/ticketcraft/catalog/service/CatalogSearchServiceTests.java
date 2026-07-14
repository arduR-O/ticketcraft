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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
        Event.builder().id(1L).title("Queen Live at Wembley").description("Magic Tour").build();
    pageable = PageRequest.of(0, 20);
  }

  @Test
  void searchEvents_withEmptyQuery_shouldReturnAllEvents() {
    Page<Event> eventPage = new PageImpl<>(List.of(testEvent));
    when(eventRepository.findAll(pageable)).thenReturn(eventPage);

    List<Event> results = catalogSearchService.searchEvents("", pageable);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTitle()).isEqualTo("Queen Live at Wembley");
    verify(eventRepository, times(1)).findAll(pageable);
    verify(eventRepository, never()).searchEvents(anyString(), any(Pageable.class));
  }

  @Test
  void searchEvents_withNullQuery_shouldReturnAllEvents() {
    Page<Event> eventPage = new PageImpl<>(List.of(testEvent));
    when(eventRepository.findAll(pageable)).thenReturn(eventPage);

    List<Event> results = catalogSearchService.searchEvents(null, pageable);

    assertThat(results).hasSize(1);
    verify(eventRepository, times(1)).findAll(pageable);
  }

  @Test
  void searchEvents_withQuery_shouldCallSearchRepository() {
    when(eventRepository.searchEvents("Queen", pageable)).thenReturn(List.of(testEvent));

    List<Event> results = catalogSearchService.searchEvents("Queen", pageable);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTitle()).isEqualTo("Queen Live at Wembley");
    verify(eventRepository, times(1)).searchEvents("Queen", pageable);
    verify(eventRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void searchEvents_withMultiWordQuery_shouldFormatToOrSearch() {
    when(eventRepository.searchEvents("Queen | Wembley", pageable)).thenReturn(List.of(testEvent));

    List<Event> results = catalogSearchService.searchEvents("Queen & Wembley!", pageable);

    assertThat(results).hasSize(1);
    verify(eventRepository, times(1)).searchEvents("Queen | Wembley", pageable);
  }
}
