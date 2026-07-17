package com.ticketcraft.catalog.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketcraft.catalog.dto.SeatStatusEvent;
import com.ticketcraft.catalog.model.SeatStatus;
import com.ticketcraft.catalog.repository.SeatRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

@ExtendWith(MockitoExtension.class)
public class SeatStatusConsumerTests {

  @Mock private SeatRepository seatRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ChannelTopic seatUpdatesTopic;
  @Mock private ObjectMapper objectMapper;

  @InjectMocks private SeatStatusConsumer seatStatusConsumer;

  @Test
  void testConsumeUpdatesDatabaseAndPublishesToRedis() throws Exception {
    SeatStatusEvent event = new SeatStatusEvent(
        1001L,
        Arrays.asList(104L, 105L),
        SeatStatus.LOCKED,
        "test-action-id",
        java.time.Instant.now()
    );

    when(seatRepository.updateSeatStatus(any(), anyLong(), any())).thenReturn(2);
    when(seatUpdatesTopic.getTopic()).thenReturn("seat-updates-topic");
    when(objectMapper.writeValueAsString(any())).thenReturn("{\"dummy\":\"json\"}");

    seatStatusConsumer.consume(event);

    verify(seatRepository).updateSeatStatus(Arrays.asList(104L, 105L), 1001L, SeatStatus.LOCKED);
    verify(redisTemplate).convertAndSend(eq("seat-updates-topic"), eq("{\"dummy\":\"json\"}"));
  }
}
