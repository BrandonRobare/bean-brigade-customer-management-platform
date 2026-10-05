package com.northstar.crm.service;

import com.northstar.crm.api.dto.CreateInteractionRequest;
import com.northstar.crm.api.dto.InteractionResponse;
import com.northstar.crm.domain.CustomerEntity;
import com.northstar.crm.domain.InteractionEntity;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {

  public static final String DEFAULT_CORRELATION = "lab-request-001";

  private final CustomerRepository customerRepository;
  private final InteractionRepository interactionRepository;

  public InteractionService(
      CustomerRepository customerRepository, InteractionRepository interactionRepository) {
    this.customerRepository = customerRepository;
    this.interactionRepository = interactionRepository;
  }

  @Transactional(readOnly = true)
  public List<InteractionResponse> list(String customerId) {
    // TODO: reject unknown customer; return interactions for public id newest-first
    if (!customerRepository.existsByPublicId(customerId)) {
      throw new IllegalArgumentException("Unknown customer: " + customerId);
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
  public InteractionResponse create(CreateInteractionRequest request, String correlationHeader) {
    // TODO: resolve customer CUS-1001; persist; return DTO (not the entity)
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

    InteractionEntity interaction = new InteractionEntity();
    interaction.setId(UUID.randomUUID());
    interaction.setCustomer(customer);
    interaction.setInteractionType(request.interactionType());
    interaction.setSummary(request.summary());
    interaction.setCorrelationId(correlationId);

    InteractionEntity saved = interactionRepository.save(interaction);
    return new InteractionResponse(
        saved.getId(),
        saved.getCustomer().getPublicId(),
        saved.getInteractionType(),
        saved.getSummary(),
        saved.getCorrelationId(),
        saved.getCreatedAt());
  }
}
