package com.northstar.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.northstar.crm.api.dto.CreateInteractionRequest;
import com.northstar.crm.api.dto.InteractionResponse;
import com.northstar.crm.domain.CustomerEntity;
import com.northstar.crm.domain.InteractionEntity;
import com.northstar.crm.messaging.CustomerInteractionRecordedV1;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class InteractionServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock InteractionRepository interactionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CustomerEntity customer;

    private InteractionService interactionService;

    @BeforeEach
    void setUp() {
        interactionService = new InteractionService(customerRepository, interactionRepository, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void createPublishesCustomerInteractionRecordedEvent() {
        CreateInteractionRequest request = new CreateInteractionRequest("CUS-1001", "NOTE", "Follow up on billing question", "lab-request-001");
        when(customerRepository.findByPublicId("CUS-1001")).thenReturn(Optional.of(customer));
        when(customer.getPublicId()).thenReturn("CUS-1001");
        when(interactionRepository.save(any(InteractionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InteractionResponse response = interactionService.create(request, "lab-request-001", "demo-agent");

        ArgumentCaptor<CustomerInteractionRecordedV1> eventCaptor = ArgumentCaptor.forClass(CustomerInteractionRecordedV1.class);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        CustomerInteractionRecordedV1 event = eventCaptor.getValue();

        assertEquals(CustomerInteractionRecordedV1.TYPE, event.eventType());
        assertEquals(CustomerInteractionRecordedV1.VERSION, event.eventVersion());
        assertEquals(response.id(), event.interactionId());
        assertEquals("CUS-1001", event.customerId());
        assertEquals("NOTE", event.interactionType());
        assertEquals("lab-request-001", event.correlationId());
        assertEquals("demo-agent", event.actor());
        assertEquals(response.createdAt(), event.occurredAt());
        assertNotNull(event.eventId());
    }

    @Test
    void unknownCustomerDoesNotSaveOrPublishEvent() {
        CreateInteractionRequest request = new CreateInteractionRequest("CUS-9999", "NOTE", "Should fail", "lab-request-001");
        when(customerRepository.findByPublicId("CUS-9999")).thenReturn(Optional.empty());

        assertThrows(UnknownCustomerException.class, () -> {
            interactionService.create(request, "lab-request-001", "demo-agent");
        });

        verifyNoInteractions(interactionRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createPutsSavedCorrelationIdInMdc() {
        CreateInteractionRequest request = new CreateInteractionRequest("CUS-1001", "NOTE", "Follow up", "body-id-7");
        when(customerRepository.findByPublicId("CUS-1001")).thenReturn(Optional.of(customer));
        when(customer.getPublicId()).thenReturn("CUS-1001");
        when(interactionRepository.save(any(InteractionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MDC.put("correlationId", "generated-by-filter");

        InteractionResponse response = interactionService.create(request, null, "demo-agent");

        assertEquals("body-id-7", response.correlationId());
        assertEquals("body-id-7", MDC.get("correlationId"));
    }

    @Test
    void createLeavesMdcAloneForInvalidCorrelationId() {
        CreateInteractionRequest request = new CreateInteractionRequest("CUS-1001", "NOTE", "Follow up", "bad\nid");
        when(customerRepository.findByPublicId("CUS-1001")).thenReturn(Optional.of(customer));
        when(customer.getPublicId()).thenReturn("CUS-1001");
        when(interactionRepository.save(any(InteractionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MDC.put("correlationId", "generated-by-filter");

        interactionService.create(request, null, "demo-agent");

        assertEquals("generated-by-filter", MDC.get("correlationId"));
    }
}
