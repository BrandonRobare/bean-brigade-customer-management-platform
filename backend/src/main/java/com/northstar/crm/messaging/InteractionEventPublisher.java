package com.northstar.crm.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InteractionEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(InteractionEventPublisher.class);

    private final KafkaTemplate<String, CustomerInteractionRecordedV1> kafkaTemplate;

    public InteractionEventPublisher(KafkaTemplate<String, CustomerInteractionRecordedV1> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(CustomerInteractionRecordedV1 event) {
        try {
            kafkaTemplate
                    .send(CustomerInteractionRecordedV1.TOPIC, event.customerId(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            logFailure(event, failure);
                        }
                    });
        } catch (RuntimeException failure) {
            logFailure(event, failure);
        }
    }

    private void logFailure(CustomerInteractionRecordedV1 event, Throwable failure) {
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", event.correlationId());
        try {
            LOG.error(
                    "Failed to publish interaction event eventId={} correlationId={}",
                    event.eventId(),
                    event.correlationId(),
                    failure);
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }
}
