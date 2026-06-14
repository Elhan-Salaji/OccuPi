package com.occupi.feature.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Room} metadata in PostgreSQL.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
}
