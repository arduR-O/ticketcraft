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
          "SELECT * FROM events WHERE search_vector @@ REPLACE(plainto_tsquery('english',"
              + " :query)::text, '&', '|')::tsquery ORDER BY ts_rank(search_vector,"
              + " REPLACE(plainto_tsquery('english', :query)::text, '&', '|')::tsquery) DESC",
      nativeQuery = true)
  List<Event> searchEvents(@Param("query") String query, Pageable pageable);

  @Query(
      value =
          "SELECT e.* FROM events e "
              + "JOIN venues v ON e.venue_id = v.id "
              + "ORDER BY (6371 * acos(cos(radians(:lat)) * cos(radians(v.latitude)) * cos(radians(v.longitude) - radians(:lng)) + sin(radians(:lat)) * sin(radians(v.latitude)))) ASC",
      nativeQuery = true)
  List<Event> findNearbyEvents(@Param("lat") double lat, @Param("lng") double lng, Pageable pageable);
}
