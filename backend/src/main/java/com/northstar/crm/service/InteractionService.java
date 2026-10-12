package com.northstar.crm.service;

import com.northstar.crm.api.CorrelationIdFilter;
import com.northstar.crm.api.dto.CreateInteractionRequest;
import com.northstar.crm.api.dto.InteractionResponse;
import com.northstar.crm.api.dto.UpdateInteractionRequest;
import com.northstar.crm.domain.CustomerEntity;
import com.northstar.crm.domain.InteractionEntity;
import com.northstar.crm.messaging.CustomerInteractionRecordedV1;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {

  private static final Logger log = LoggerFactory.getLogger(InteractionService.class);

  @PersistenceContext
  private EntityManager entityManager;

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
        .map(entity -> response(entity, entity.getVersion()))
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

    return response(saved, saved.getVersion());
  }

  @Transactional
  public InteractionResponse update(UUID id, UpdateInteractionRequest request) {
    InteractionEntity interaction = current(id, request.version());
    String type = request.interactionType() == null ? interaction.getInteractionType() : request.interactionType();
    String summary = request.summary() == null ? interaction.getSummary() : request.summary();
    boolean unchanged = Objects.equals(type, interaction.getInteractionType())
        && Objects.equals(summary, interaction.getSummary());
    interaction.setInteractionType(type);
    interaction.setSummary(summary);
    if (unchanged) {
      entityManager.lock(interaction, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
    interactionRepository.flush();
    log.info("Edited interaction interactionId={} correlationId={}", id, MDC.get(CorrelationIdFilter.MDC_KEY));
    return response(interaction, interaction.getVersion() + (unchanged ? 1 : 0));
  }

  @Transactional
  public void delete(UUID id, long version) {
    InteractionEntity interaction = current(id, version);
    interactionRepository.delete(interaction);
    interactionRepository.flush();
    log.info("Deleted interaction interactionId={} correlationId={}", id, MDC.get(CorrelationIdFilter.MDC_KEY));
  }

  private InteractionEntity current(UUID id, long version) {
    InteractionEntity interaction = interactionRepository.findById(id)
        .orElseThrow(() -> new UnknownInteractionException(id));
    if (interaction.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(InteractionEntity.class, id);
    }
    return interaction;
  }

  private InteractionResponse response(InteractionEntity interaction, long version) {
    return new InteractionResponse(
        interaction.getId(),
        interaction.getCustomer().getPublicId(),
        interaction.getInteractionType(),
        interaction.getSummary(),
        interaction.getCorrelationId(),
        interaction.getCreatedAt(),
        version);
  }
}
