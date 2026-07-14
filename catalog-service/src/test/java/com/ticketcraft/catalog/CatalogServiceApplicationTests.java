package com.ticketcraft.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.boot.test.mock.mockito.MockBean;
import com.ticketcraft.catalog.repository.EventRepository;
import com.ticketcraft.catalog.repository.SeatRepository;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
    "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
@ActiveProfiles("test")
class CatalogServiceApplicationTests {

    @MockBean
    private EventRepository eventRepository;

    @MockBean
    private SeatRepository seatRepository;

    @Test
    void contextLoads() {
        // Verification that the Spring Application Context starts cleanly with DB configurations disabled for testing
    }
}
