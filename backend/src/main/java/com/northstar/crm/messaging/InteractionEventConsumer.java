package com.northstar.crm.messaging;

import com.northstar.crm.repo.ProcessedEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class InteractionEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionEventConsumer.class);

    private final ProcessedEventRepository processedEvents;

    private final Counter processed;

    public InteractionEventConsumer(ProcessedEventRepository processedEvents, MeterRegistry meters) {
        this.processedEvents = processedEvents;
        this.processed = meters.counter("crm.kafka.messages", "topic", CustomerInteractionRecordedV1.TOPIC, "outcome", "processed");
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
            } else {
                LOG.info(
                    "Handled interaction event eventId={} correlationId={} interactionId={} customerId={}",
                    event.eventId(),
                    event.correlationId(),
                    event.interactionId(),
                    event.customerId());
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processed.increment();
                }
            });
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
