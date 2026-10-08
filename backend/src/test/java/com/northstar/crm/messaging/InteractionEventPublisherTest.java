package com.northstar.crm.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class InteractionEventPublisherTest {

    @Mock
    KafkaTemplate<String, CustomerInteractionRecordedV1> kafkaTemplate;

    private InteractionEventPublisher publisher;
    private CustomerInteractionRecordedV1 event;

    @BeforeEach
    void setUp() {
        publisher = new InteractionEventPublisher(kafkaTemplate);
        event = new CustomerInteractionRecordedV1(
                CustomerInteractionRecordedV1.TYPE,
                CustomerInteractionRecordedV1.VERSION,
                UUID.randomUUID(),
                "CUS-1001",
                "NOTE",
                "lab-request-001",
                Instant.now(),
                UUID.randomUUID(),
                "demo-agent"
        );
    }

    @Test
    void publishesUsingExpectedTopicCustomerIdKeyAndEventValue() {
        when(kafkaTemplate.send(CustomerInteractionRecordedV1.TOPIC, event.customerId(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        verify(kafkaTemplate).send(CustomerInteractionRecordedV1.TOPIC, event.customerId(), event);
    }

    @Test
    void handlesAsynchronousKafkaFailure() {
        RuntimeException failure = new RuntimeException("Kafka unavailable");

        when(kafkaTemplate.send(CustomerInteractionRecordedV1.TOPIC, event.customerId(), event))
                .thenReturn(CompletableFuture.<SendResult<String, CustomerInteractionRecordedV1>>failedFuture(failure));

        assertDoesNotThrow(() -> publisher.publish(event));

        verify(kafkaTemplate).send(CustomerInteractionRecordedV1.TOPIC, "CUS-1001", event);
    }

    @Test
    void handlesImmediateKafkaFailure() {
        when(kafkaTemplate.send(CustomerInteractionRecordedV1.TOPIC, event.customerId(), event))
                .thenThrow(new RuntimeException("Kafka send failed"));

        assertDoesNotThrow(() -> publisher.publish(event));

        verify(kafkaTemplate).send(CustomerInteractionRecordedV1.TOPIC, "CUS-1001", event);
    }
}