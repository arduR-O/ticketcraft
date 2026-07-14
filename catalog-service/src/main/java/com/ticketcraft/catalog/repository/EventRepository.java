package com.ticketcraft.catalog.repository;

import com.ticketcraft.catalog.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query(value = "SELECT * FROM events " +
            "WHERE search_vector @@ plainto_tsquery('english', :query)",
            nativeQuery = true)
    List<Event> searchEvents(@Param("query") String query);
}
