package com.ticketcraft.catalog.repository;

import com.ticketcraft.catalog.model.Artist;
import com.ticketcraft.catalog.model.Event;
import com.ticketcraft.catalog.model.Seat;
import com.ticketcraft.catalog.model.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CatalogRepositoryTests {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void testDatabaseMappingsAndQueries() {
        // 1. Create and Save Artist
        Artist artist = Artist.builder()
                .name("Queen")
                .genre("Rock")
                .description("British rock band formed in London in 1970.")
                .build();
        Artist savedArtist = artistRepository.save(artist);
        assertThat(savedArtist.getId()).isNotNull();

        // 2. Create and Save Venue
        Venue venue = Venue.builder()
                .name("Wembley Stadium")
                .location("London, UK")
                .capacity(90000)
                .build();
        Venue savedVenue = venueRepository.save(venue);
        assertThat(savedVenue.getId()).isNotNull();

        // 3. Create and Save Event
        Event event = Event.builder()
                .title("Queen Live at Wembley")
                .description("Magic Tour Concert")
                .date(LocalDateTime.now().plusDays(30))
                .artist(savedArtist)
                .venue(savedVenue)
                .build();
        Event savedEvent = eventRepository.save(event);
        assertThat(savedEvent.getId()).isNotNull();

        // 4. Create and Save Seats
        Seat seat1 = Seat.builder()
                .seatNumber("A-101")
                .rowNumber("Row-10")
                .category("VIP")
                .status("AVAILABLE")
                .price(new BigDecimal("150.00"))
                .event(savedEvent)
                .build();

        Seat seat2 = Seat.builder()
                .seatNumber("A-102")
                .rowNumber("Row-10")
                .category("STANDARD")
                .status("AVAILABLE")
                .price(new BigDecimal("80.00"))
                .event(savedEvent)
                .build();

        seatRepository.saveAll(List.of(seat1, seat2));

        // 5. Query and Assert
        List<Seat> seats = seatRepository.findByEventId(savedEvent.getId());
        assertThat(seats).hasSize(2);
        assertThat(seats).extracting(Seat::getSeatNumber).containsExactlyInAnyOrder("A-101", "A-102");
    }
}
