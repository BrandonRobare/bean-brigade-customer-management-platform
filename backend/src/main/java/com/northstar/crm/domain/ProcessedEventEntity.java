package com.northstar.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
public class ProcessedEventEntity {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt = Instant.now();

  protected ProcessedEventEntity() {}

  public ProcessedEventEntity(UUID eventId) {
    this.eventId = eventId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
