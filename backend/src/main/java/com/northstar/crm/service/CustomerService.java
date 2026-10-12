package com.northstar.crm.service;

import com.northstar.crm.api.CorrelationIdFilter;
import com.northstar.crm.api.dto.ChangeCustomerStatusRequest;
import com.northstar.crm.api.dto.CreateCustomerRequest;
import com.northstar.crm.api.dto.CustomerDTO;
import com.northstar.crm.api.dto.UpdateCustomerRequest;
import com.northstar.crm.domain.CustomerEntity;
import com.northstar.crm.mapper.CustomerMapper;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

  private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
  @PersistenceContext
  private EntityManager entityManager;
  private final InteractionRepository interactions;
  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository, InteractionRepository interactions) {
    this.customerRepository = customerRepository;
    this.interactions = interactions;
  }

  public List<CustomerDTO> search(String query) {
    String term = query.strip();
    return customerRepository
        .findByFullNameContainingIgnoreCaseOrPublicIdIgnoreCaseOrderByPublicId(term, term)
        .stream()
        .map(CustomerMapper::toCustomerDTO)
        .toList();
  }

  public CustomerDTO getCustomerById(String publicId) {
    return customerRepository
        .findByPublicId(publicId)
        .map(CustomerMapper::toCustomerDTO)
        .orElseThrow(() -> new UnknownCustomerException(publicId));
  }

  @Transactional
  public CustomerDTO create(CreateCustomerRequest request) {
    CustomerEntity customer = new CustomerEntity();
    customer.setPublicId("CUS-" + customerRepository.nextPublicNumber());
    customer.setFullName(request.fullName());
    customer.setStatus("PROSPECT");
    customerRepository.saveAndFlush(customer);
    log.info("Created customer customerId={} correlationId={}", customer.getPublicId(), MDC.get(CorrelationIdFilter.MDC_KEY));
    return CustomerMapper.toCustomerDTO(customer);
  }

  @Transactional
  public CustomerDTO update(String publicId, UpdateCustomerRequest request) {
    CustomerEntity customer = current(publicId, request.version());
    boolean unchanged = customer.getFullName().equals(request.fullName());
    customer.setFullName(request.fullName());
    if (unchanged) {
      entityManager.lock(customer, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
    customerRepository.flush();
    log.info("Edited customer customerId={} correlationId={}", publicId, MDC.get(CorrelationIdFilter.MDC_KEY));
    return new CustomerDTO(customer.getPublicId(), customer.getFullName(), customer.getStatus(),
        customer.getCreatedAt().toString(), customer.getVersion() + (unchanged ? 1 : 0));
  }

  @Transactional
  public CustomerDTO changeStatus(String publicId, ChangeCustomerStatusRequest request) {
    CustomerEntity customer = current(publicId, request.version());
    String status = customer.getStatus();
    boolean allowed = status.equals("PROSPECT") && !request.status().equals("PROSPECT")
        || status.equals("ACTIVE") && request.status().equals("CLOSED");
    if (!allowed) {
      throw new CustomerConflictException("Customer status transition is not allowed");
    }
    customer.setStatus(request.status());
    customerRepository.flush();
    log.info("Changed customer status customerId={} correlationId={}", publicId, MDC.get(CorrelationIdFilter.MDC_KEY));
    return CustomerMapper.toCustomerDTO(customer);
  }

  @Transactional
  public void delete(String publicId, long version) {
    CustomerEntity customer = current(publicId, version);
    if (interactions.existsByCustomer_PublicId(publicId)) {
      throw new CustomerConflictException("Customer has interaction history and cannot be deleted");
    }
    try {
      customerRepository.delete(customer);
      customerRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new CustomerConflictException("Customer has interaction history and cannot be deleted");
    }
    log.info("Deleted customer customerId={} correlationId={}", publicId, MDC.get(CorrelationIdFilter.MDC_KEY));
  }

  private CustomerEntity current(String publicId, long version) {
    CustomerEntity customer = customerRepository.findByPublicId(publicId)
        .orElseThrow(() -> new UnknownCustomerException(publicId));
    if (customer.getVersion() != version) {
      throw new ObjectOptimisticLockingFailureException(CustomerEntity.class, publicId);
    }
    return customer;
  }
}
