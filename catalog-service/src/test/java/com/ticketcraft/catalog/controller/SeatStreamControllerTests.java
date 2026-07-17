package com.ticketcraft.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketcraft.catalog.dto.SeatStatusUpdate;
import com.ticketcraft.catalog.model.SeatStatus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(SeatStreamController.class)
public class SeatStreamControllerTests {

  @Autowired private MockMvc mockMvc;
  
  @Autowired private SeatStreamController seatStreamController;

  @Test
  void testSubscribeReturnsSseEmitter() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/events/1001/seat-stream")
            .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
        .andReturn();

    assertNotNull(result.getResponse().getContentAsString());
  }

  @Test
  void testBroadcastDoesNotThrowExceptions() {
    SeatStatusUpdate update = new SeatStatusUpdate(104L, SeatStatus.LOCKED);
    
    // It shouldn't throw anything if we just broadcast to nowhere or if clients are connected
    seatStreamController.broadcast(1001L, Arrays.asList(update));
  }
}
