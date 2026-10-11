package com.northstar.crm.messaging;

import com.northstar.crm.repo.ProcessedEventRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InteractionEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionEventConsumer.class);

    private final ProcessedEventRepository processedEvents;

    public InteractionEventConsumer(ProcessedEventRepository processedEvents) {
        this.processedEvents = processedEvents;
    }

    @Transactional
    @KafkaListener(topics = CustomerInteractionRecordedV1.TOPIC)
    public void receive(CustomerInteractionRecordedV1 event) {
        String previous = MDC.get("correlationId");
        if (event.correlationId() == null) {
            MDC.remove("correlationId");
        } else {
            MDC.put("correlationId", event.correlationId());
        }
        try {
            validate(event);
            if (processedEvents.insertIfNew(event.eventId()) == 0) {
                LOG.info(
                        "Skipping duplicate interaction event eventId={} correlationId={}",
                        event.eventId(),
                        event.correlationId());
                return;
            }

            LOG.info(
                    "Handled interaction event eventId={} correlationId={} interactionId={} customerId={}",
                    event.eventId(),
                    event.correlationId(),
                    event.interactionId(),
                    event.customerId());
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }

    private void validate(CustomerInteractionRecordedV1 event) {
        if (event.eventId() == null) {
            throw new InvalidInteractionEventException("Interaction eventId is required");
        }
        if (!Objects.equals(CustomerInteractionRecordedV1.VERSION, event.eventVersion())) {
            throw new InvalidInteractionEventException("Unsupported interaction event version");
        }
    }
}

class InvalidInteractionEventException extends RuntimeException {

    InvalidInteractionEventException(String message) {
        super(message);
    }
}
