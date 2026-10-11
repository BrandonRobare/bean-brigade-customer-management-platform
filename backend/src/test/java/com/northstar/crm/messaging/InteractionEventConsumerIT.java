package com.northstar.crm.messaging;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northstar.crm.repo.ProcessedEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.listener.auto-startup=true"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
    CustomerInteractionRecordedV1.TOPIC,
    CustomerInteractionRecordedV1.TOPIC + ".DLT"
})
@ExtendWith(OutputCaptureExtension.class)
class InteractionEventConsumerIT {

    private static final String DLT = CustomerInteractionRecordedV1.TOPIC + ".DLT";

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/crm");
        registry.add("spring.datasource.username", () -> "crm");
        registry.add("spring.datasource.password", () -> "change-me");
    }

    @Autowired KafkaTemplate<String, CustomerInteractionRecordedV1> kafkaTemplate;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired KafkaListenerEndpointRegistry listenerRegistry;
    @Autowired ObjectMapper json;

    @MockitoSpyBean ProcessedEventRepository processedEvents;
    @MockitoSpyBean InteractionEventConsumer eventConsumer;

    private final Map<UUID, Boolean> eventIds = new ConcurrentHashMap<>();
    private Consumer<String, byte[]> dltConsumer;

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        dltConsumer = new DefaultKafkaConsumerFactory<String, byte[]>(consumerProperties).createConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, true, DLT);
    }

    @AfterEach
    void cleanUp() {
        dltConsumer.close();
        processedEvents.deleteAllById(eventIds.keySet());
        eventIds.clear();
    }

    @Test
    void validV1EventIsRecordedAndLogsCorrelationId(CapturedOutput output) throws Exception {
        CustomerInteractionRecordedV1 event = event("1");

        send(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertTrue(processedEvents.existsById(event.eventId())));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertTrue(output.getAll().contains(event.correlationId())));
    }

    @Test
    void duplicateEventIsSkippedWithoutDeadLetter(CapturedOutput output) throws Exception {
        CustomerInteractionRecordedV1 event = event("1");

        send(event);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertTrue(processedEvents.existsById(event.eventId())));
        send(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(eventConsumer, times(2)).receive(argThat(received -> received.eventId().equals(event.eventId())));
            assertEquals(1, processedEvents.findAllById(eventIds.keySet()).size());
            assertTrue(output.getAll().contains("Skipping duplicate interaction event eventId=" + event.eventId()));
        });
        assertTrue(KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(1)).isEmpty());
    }

    @Test
    void unsupportedVersionIsDeadLetteredWithoutRetry() throws Exception {
        CustomerInteractionRecordedV1 event = event("2");

        send(event);

        ConsumerRecord<String, byte[]> deadLetter = KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(10));
        assertEquals(event.eventId(), json.readValue(deadLetter.value(), CustomerInteractionRecordedV1.class).eventId());
        verify(eventConsumer, times(1)).receive(argThat(received -> received.eventId().equals(event.eventId())));
        assertFalse(processedEvents.existsById(event.eventId()));
    }

    @Test
    void transientFailureRetriesTwiceThenDeadLetters() throws Exception {
        CustomerInteractionRecordedV1 event = event("1");
        doThrow(new RuntimeException("synthetic transient failure"))
                .when(processedEvents).insertIfNew(event.eventId());

        send(event);

        ConsumerRecord<String, byte[]> deadLetter = KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(15));
        assertEquals(event.eventId(), json.readValue(deadLetter.value(), CustomerInteractionRecordedV1.class).eventId());
        verify(eventConsumer, times(3)).receive(argThat(received -> received.eventId().equals(event.eventId())));
        verify(processedEvents, times(3)).insertIfNew(event.eventId());
        assertFalse(processedEvents.existsById(event.eventId()));
    }

    @Test
    void malformedPayloadIsDeadLetteredUnchangedAndNextEventIsProcessed() throws Exception {
        byte[] malformed = "not-json".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> producerProperties = KafkaTestUtils.producerProps(broker);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaTemplate<String, byte[]> rawKafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));

        rawKafkaTemplate.send(CustomerInteractionRecordedV1.TOPIC, "CUS-1001", malformed).get();

        ConsumerRecord<String, byte[]> deadLetter = KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(10));
        assertArrayEquals(malformed, deadLetter.value());

        CustomerInteractionRecordedV1 valid = event("1");
        send(valid);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertTrue(processedEvents.existsById(valid.eventId())));
    }

    private CustomerInteractionRecordedV1 event(String version) {
        UUID eventId = UUID.randomUUID();
        eventIds.put(eventId, true);
        return new CustomerInteractionRecordedV1(
                CustomerInteractionRecordedV1.TYPE,
                version,
                UUID.randomUUID(),
                "CUS-1001",
                "NOTE",
                "consumer-it-" + eventId,
                Instant.now(),
                eventId,
                "demo-agent");
    }

    private void send(CustomerInteractionRecordedV1 event) throws Exception {
        kafkaTemplate.send(CustomerInteractionRecordedV1.TOPIC, event.customerId(), event).get();
    }
}
