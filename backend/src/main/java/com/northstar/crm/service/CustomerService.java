package com.northstar.crm.service;

import com.northstar.crm.api.dto.CustomerDTO;
import com.northstar.crm.mapper.CustomerMapper;
import com.northstar.crm.repo.CustomerRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
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
}
