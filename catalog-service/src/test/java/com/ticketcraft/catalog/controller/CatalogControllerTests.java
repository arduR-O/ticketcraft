package com.ticketcraft.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.service.CatalogSearchService;
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
    Event event = Event.builder().id(1L).title("Queen Live at Wembley").build();

    when(catalogSearchService.searchEvents(eq("Queen"), any(Pageable.class)))
        .thenReturn(List.of(event));

    mockMvc
        .perform(
            get("/api/events/search")
                .param("query", "Queen")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].title").value("Queen Live at Wembley"));
  }
}
