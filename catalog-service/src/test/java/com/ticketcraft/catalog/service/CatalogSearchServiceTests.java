package com.ticketcraft.catalog.service;

import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.repository.EventRepository;
import com.ticketcraft.catalog.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogSearchServiceTests {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private CatalogSearchService catalogSearchService;

    private Event testEvent;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(1L)
                .title("Queen Live at Wembley")
                .description("Magic Tour")
                .build();
    }

    @Test
    void searchEvents_withEmptyQuery_shouldReturnAllEvents() {
        when(eventRepository.findAll()).thenReturn(List.of(testEvent));

        List<Event> results = catalogSearchService.searchEvents("");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Queen Live at Wembley");
        verify(eventRepository, times(1)).findAll();
        verify(eventRepository, never()).searchEvents(anyString());
    }

    @Test
    void searchEvents_withNullQuery_shouldReturnAllEvents() {
        when(eventRepository.findAll()).thenReturn(List.of(testEvent));

        List<Event> results = catalogSearchService.searchEvents(null);

        assertThat(results).hasSize(1);
        verify(eventRepository, times(1)).findAll();
    }

    @Test
    void searchEvents_withQuery_shouldCallSearchRepository() {
        when(eventRepository.searchEvents("Queen")).thenReturn(List.of(testEvent));

        List<Event> results = catalogSearchService.searchEvents("Queen");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Queen Live at Wembley");
        verify(eventRepository, times(1)).searchEvents("Queen");
        verify(eventRepository, never()).findAll();
    }
}
