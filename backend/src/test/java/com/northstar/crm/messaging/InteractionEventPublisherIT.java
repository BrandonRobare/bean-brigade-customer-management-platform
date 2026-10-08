package com.northstar.crm.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.northstar.crm.api.dto.CreateInteractionRequest;
import com.northstar.crm.api.dto.InteractionResponse;
import com.northstar.crm.repo.InteractionRepository;
import com.northstar.crm.service.InteractionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


@SpringBootTest
@ActiveProfiles("test")
class InteractionEventPublisherIT {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/crm");
        registry.add("spring.datasource.username", () -> "crm");
        registry.add("spring.datasource.password", () -> "change-me");
    }

    @Autowired InteractionService interactionService;
    @Autowired InteractionRepository interactionRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @MockBean KafkaTemplate<String, CustomerInteractionRecordedV1> kafkaTemplate;

    private TransactionTemplate transactionTemplate;

    private final List<UUID> committedInteractionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        reset(kafkaTemplate);

        CompletableFuture<SendResult<String, CustomerInteractionRecordedV1>>
                success = CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(anyString(), anyString(), any(CustomerInteractionRecordedV1.class)))
                .thenReturn(success);
    }

    @AfterEach
    void cleanUp() {
        for (UUID interactionId : committedInteractionIds) {
            interactionRepository.deleteById(interactionId);
        }
        committedInteractionIds.clear();
    }

    @Test
    void committedTransactionPublishEvent() {
        CreateInteractionRequest request = new CreateInteractionRequest("CUS-1001", "NOTE", "Commit integration test", "it-commit-001");

        InteractionResponse response = transactionTemplate.execute(status ->
            interactionService.create(request, "it-commit-001", "demo-agent"));

        assertNotNull(response);
        committedInteractionIds.add(response.id());

        assertTrue(interactionRepository.existsById(response.id()));

        verify(kafkaTemplate).send(eq(CustomerInteractionRecordedV1.TOPIC), eq("CUS-1001"), any(CustomerInteractionRecordedV1.class));
    }

    @Test
    void rolledBackTransactionDoesNotPublishEvent() {
        CreateInteractionRequest request = new CreateInteractionRequest("CUS-1001", "NOTE", "Rollback integration test", "it-rollback-001");

        InteractionResponse response = transactionTemplate.execute(status -> {
            InteractionResponse created = interactionService.create(request, "it-rollback-001", "demo-agent");

            status.setRollbackOnly();

            return created;
        });

        assertNotNull(response);
        assertFalse(interactionRepository.existsById(response.id()));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(CustomerInteractionRecordedV1.class));
    }

    @Test
    void kafkaFailureAfterCommitLeavesInteractionStored() {
        CompletableFuture<SendResult<String, CustomerInteractionRecordedV1>>
                failedSend = CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable"));

        when(kafkaTemplate.send(anyString(), anyString(), any(CustomerInteractionRecordedV1.class)))
                .thenReturn(failedSend);

        CreateInteractionRequest request = new CreateInteractionRequest("CUS-1001", "NOTE", "Kafka failure integration test", "it-kafka-failure-001");

        InteractionResponse response = transactionTemplate.execute(status ->
            interactionService.create(request, "it-kafka-failure-001", "demo-agent"));

        assertNotNull(response);
        committedInteractionIds.add(response.id());
        assertTrue(interactionRepository.existsById(response.id()));

        verify(kafkaTemplate).send(eq(CustomerInteractionRecordedV1.TOPIC), eq("CUS-1001"), any(CustomerInteractionRecordedV1.class));
    }

}