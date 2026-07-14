package com.ticketcraft.catalog.repository;

import com.ticketcraft.catalog.model.Event;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

  @Query(
      value =
          "SELECT * FROM events "
              + "WHERE search_vector @@ to_tsquery('english', :query) "
              + "ORDER BY ts_rank(search_vector, to_tsquery('english', :query)) DESC",
      nativeQuery = true)
  List<Event> searchEvents(@Param("query") String query, Pageable pageable);
}
