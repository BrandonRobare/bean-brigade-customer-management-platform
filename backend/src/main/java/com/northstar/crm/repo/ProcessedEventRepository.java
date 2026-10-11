package com.northstar.crm.repo;

import com.northstar.crm.domain.ProcessedEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {

  @Modifying
  @Query(value = "INSERT INTO processed_event (event_id) VALUES (:eventId) ON CONFLICT DO NOTHING", nativeQuery = true)
  int insertIfNew(UUID eventId);
}
