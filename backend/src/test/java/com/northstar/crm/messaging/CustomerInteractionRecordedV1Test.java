package com.northstar.crm.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CustomerInteractionRecordedV1Test {

  @Test
  void usesFrozenContractConstatns() {
      assertEquals("crm.customer.interactions.v1", CustomerInteractionRecordedV1.TOPIC);
      assertEquals("CustomerInteractionRecorded", CustomerInteractionRecordedV1.TYPE);
      assertEquals("1", CustomerInteractionRecordedV1.VERSION);
  }

  @Test
    void storesRequiredEventFields() {
      UUID eventId = UUID.randomUUID();
      UUID interactionId = UUID.randomUUID();
      Instant occurredAt = Instant.now();

      CustomerInteractionRecordedV1 event = new CustomerInteractionRecordedV1(
              CustomerInteractionRecordedV1.TYPE,
              CustomerInteractionRecordedV1.VERSION,
              interactionId,
              "CUS-1001",
              "NOTE",
              "lab-request-001",
              occurredAt,
              eventId,
              "demo-agent"
      );

      assertEquals(eventId, event.eventId());
      assertEquals(interactionId, event.interactionId());
      assertEquals("CUS-1001", event.customerId());
      assertEquals("NOTE", event.interactionType());
      assertEquals("lab-request-001", event.correlationId());
      assertEquals("demo-agent", event.actor());
      assertEquals(occurredAt, event.occurredAt());
      assertNotNull(event.eventId());
  }

  @Test
    void contractDoesNotContainSummary() {
      boolean containsSummary = Arrays.stream(CustomerInteractionRecordedV1.class.getRecordComponents())
              .anyMatch(component -> component.getName().equals("summary"));

      assertFalse(containsSummary);
  }

  @Test
  void serializesOccurredAtAsIso8601String() throws Exception {
    Instant occurredAt =
            Instant.parse("2026-10-08T18:33:07.602126700Z");

    CustomerInteractionRecordedV1 event =
            new CustomerInteractionRecordedV1(
                    CustomerInteractionRecordedV1.TYPE,
                    CustomerInteractionRecordedV1.VERSION,
                    UUID.randomUUID(),
                    "CUS-1001",
                    "NOTE",
                    "lab-request-004",
                    occurredAt,
                    UUID.randomUUID(),
                    "demo-agent");

    ObjectMapper objectMapper = new ObjectMapper();

    String json = objectMapper.writeValueAsString(event);

    assertTrue(
            json.contains(
                    "\"occurredAt\":\"2026-10-08T18:33:07.602126700Z\""));
  }
}