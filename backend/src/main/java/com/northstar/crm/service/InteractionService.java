package com.northstar.crm.service;

import com.northstar.crm.api.CorrelationIdFilter;
import com.northstar.crm.api.dto.CreateInteractionRequest;
import com.northstar.crm.api.dto.InteractionResponse;
import com.northstar.crm.domain.CustomerEntity;
import com.northstar.crm.domain.InteractionEntity;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import com.northstar.crm.messaging.CustomerInteractionRecordedV1;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class InteractionService {

  public static final String DEFAULT_CORRELATION = "lab-request-001";

  private final CustomerRepository customerRepository;
  private final InteractionRepository interactionRepository;
  private final ApplicationEventPublisher eventPublisher;

  public InteractionService(
      CustomerRepository customerRepository, InteractionRepository interactionRepository, ApplicationEventPublisher eventPublisher) {
    this.customerRepository = customerRepository;
    this.interactionRepository = interactionRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional(readOnly = true)
  public List<InteractionResponse> list(String customerId) {
    if (!customerRepository.existsByPublicId(customerId)) {
      throw new UnknownCustomerException(customerId);
    }
    return interactionRepository.findByCustomer_PublicIdOrderByCreatedAtDesc(customerId).stream()
        .map(
            entity ->
                new InteractionResponse(
                    entity.getId(),
                    entity.getCustomer().getPublicId(),
                    entity.getInteractionType(),
                    entity.getSummary(),
                    entity.getCorrelationId(),
                    entity.getCreatedAt()))
        .toList();
  }

  @Transactional
  public InteractionResponse create(CreateInteractionRequest request, String correlationHeader, String actor) {
    CustomerEntity customer =
        customerRepository
            .findByPublicId(request.customerId())
            .orElseThrow(() -> new UnknownCustomerException(request.customerId()));

    String correlationId = request.correlationId();
    if (correlationHeader != null && !correlationHeader.isBlank()) {
      correlationId = correlationHeader;
    } else if (correlationId == null || correlationId.isBlank()) {
      correlationId = DEFAULT_CORRELATION;
    }
    if (CorrelationIdFilter.isValid(correlationId)) {
      MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
    }

    InteractionEntity interaction = new InteractionEntity();
    interaction.setId(UUID.randomUUID());
    interaction.setCustomer(customer);
    interaction.setInteractionType(request.interactionType());
    interaction.setSummary(request.summary());
    interaction.setCorrelationId(correlationId);

    InteractionEntity saved = interactionRepository.save(interaction);

    CustomerInteractionRecordedV1 event = new CustomerInteractionRecordedV1(
            CustomerInteractionRecordedV1.TYPE,
            CustomerInteractionRecordedV1.VERSION,
            saved.getId(),
            saved.getCustomer().getPublicId(),
            saved.getInteractionType(),
            saved.getCorrelationId(),
            saved.getCreatedAt(),
            UUID.randomUUID(),
            actor);

    eventPublisher.publishEvent(event);

    return new InteractionResponse(
        saved.getId(),
        saved.getCustomer().getPublicId(),
        saved.getInteractionType(),
        saved.getSummary(),
        saved.getCorrelationId(),
        saved.getCreatedAt());
  }
}
