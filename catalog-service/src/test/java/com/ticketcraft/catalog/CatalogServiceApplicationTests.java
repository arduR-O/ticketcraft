package com.ticketcraft.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
    "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
@ActiveProfiles("test")
class CatalogServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verification that the Spring Application Context starts cleanly with DB configurations disabled for testing
    }
}
