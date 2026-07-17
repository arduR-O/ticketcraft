package com.ticketcraft.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ticketcraft.catalog.dto.EventResponse;
import com.ticketcraft.catalog.service.CatalogSearchService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogController.class)
@ActiveProfiles("test")
class CatalogControllerTests {

  @Autowired private MockMvc mockMvc;

  @MockBean private CatalogSearchService catalogSearchService;

  @Test
  void searchEvents_shouldReturnEvents() throws Exception {
    EventResponse eventResponse =
        new EventResponse(
            1L,
            "Queen Live at Wembley",
            "Magic Tour",
            LocalDateTime.now(),
            "Queen",
            "Wembley Stadium",
            "London");

    when(catalogSearchService.searchEvents(eq("Queen"), any(Pageable.class)))
        .thenReturn(List.of(eventResponse));

    mockMvc
        .perform(
            get("/api/events/search")
                .param("query", "Queen")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].title").value("Queen Live at Wembley"));
  }

  @Test
  void getEventDetail_shouldReturnFullEventDetails() throws Exception {
    com.ticketcraft.catalog.dto.EventDetailResponse detail =
        new com.ticketcraft.catalog.dto.EventDetailResponse(
            1001L,
            "Queen Live at Wembley",
            "Magic Tour",
            LocalDateTime.now(),
            "Queen",
            "Wembley Stadium",
            "London",
            51.556,
            -0.2795,
            100L,
            87L,
            java.util.Map.of("VIP", java.math.BigDecimal.valueOf(150.00)));

    when(catalogSearchService.getEventDetail(eq(1001L))).thenReturn(detail);

    mockMvc
        .perform(get("/api/events/1001").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1001))
        .andExpect(jsonPath("$.title").value("Queen Live at Wembley"))
        .andExpect(jsonPath("$.venueLatitude").value(51.556))
        .andExpect(jsonPath("$.availableSeats").value(87))
        .andExpect(jsonPath("$.pricingTiers.VIP").value(150.00));
  }

  @Test
  void searchEvents_shouldReturnBadRequest_whenPageNumberIsNegative() throws Exception {
    mockMvc
        .perform(
            get("/api/events/search")
                .param("query", "Queen")
                .param("page", "-1")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Page number must be >= 0"))
        .andExpect(jsonPath("$.path").value("/api/events/search"));
  }

  @Test
  void getEventDetail_shouldReturnNotFound_whenEventDoesNotExist() throws Exception {
    when(catalogSearchService.getEventDetail(eq(999L)))
        .thenThrow(
            new com.ticketcraft.catalog.exception.EventNotFoundException(
                "Event not found with ID: 999"));

    mockMvc
        .perform(get("/api/events/999").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Event not found with ID: 999"))
        .andExpect(jsonPath("$.path").value("/api/events/999"));
  }
}
