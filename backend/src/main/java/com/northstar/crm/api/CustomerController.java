package com.northstar.crm.api;

import com.northstar.crm.api.dto.CustomerDTO;
import com.northstar.crm.service.CustomerService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@CrossOrigin(
    origins = "http://localhost:4200",
    allowedHeaders = {"Authorization", "Content-Type", "X-Correlation-ID", "X-Correlation-Id"})
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  public List<CustomerDTO> search(@RequestParam(defaultValue = "") String query) {
    return customerService.search(query);
  }

  @GetMapping("/{id}")
  public CustomerDTO getCustomerById(@PathVariable String id) {
    return customerService.getCustomerById(id);
  }
}
