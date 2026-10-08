package com.northstar.crm.messaging;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Versioned event contract stub.
 */
public record CustomerInteractionRecordedV1(
    String eventType,
    String eventVersion,
    UUID interactionId,
    String customerId,
    String interactionType,
    String correlationId,
    @JsonSerialize(using = ToStringSerializer.class)
    Instant occurredAt,
    UUID eventId,
    String actor
) {
  public static final String TYPE = "CustomerInteractionRecorded";
  public static final String TOPIC = "crm.customer.interactions.v1";
  public static final String VERSION = "1";
}
