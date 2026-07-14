package com.ticketcraft.catalog.repository;

import com.ticketcraft.catalog.model.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueRepository
        extends
            JpaRepository<Venue, Long> {
}
