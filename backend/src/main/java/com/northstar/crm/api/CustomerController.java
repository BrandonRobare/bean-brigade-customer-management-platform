package com.northstar.crm.api;

import com.northstar.crm.api.dto.CustomerDTO;
import com.northstar.crm.service.CustomerService;
import com.northstar.crm.service.InvalidCustomerIdException;
import java.util.List;
import java.net.URI;
import com.northstar.crm.api.dto.CreateCustomerRequest;
import com.northstar.crm.api.dto.UpdateCustomerRequest;
import com.northstar.crm.api.dto.ChangeCustomerStatusRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
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
    if (!id.matches("CUS-\\d+")) {
      throw new InvalidCustomerIdException(id);
    }
    return customerService.getCustomerById(id);
  }

  @PostMapping
  public ResponseEntity<CustomerDTO> create(
      @Valid @RequestBody CreateCustomerRequest request,
      @RequestHeader(value = "X-Correlation-ID", required = false)
          @Size(max = 64) @Pattern(regexp = "(?s).*\\S.*") String correlationHeader) {
    CustomerDTO customer = customerService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.publicId())).body(customer);
  }

  @PutMapping("/{publicId}")
  public CustomerDTO update(
      @PathVariable @Pattern(regexp = "CUS-\\d+") String publicId,
      @Valid @RequestBody UpdateCustomerRequest request,
      @RequestHeader(value = "X-Correlation-ID", required = false)
          @Size(max = 64) @Pattern(regexp = "(?s).*\\S.*") String correlationHeader) {
    return customerService.update(publicId, request);
  }

  @PatchMapping("/{publicId}/status")
  public CustomerDTO changeStatus(
      @PathVariable @Pattern(regexp = "CUS-\\d+") String publicId,
      @Valid @RequestBody ChangeCustomerStatusRequest request,
      @RequestHeader(value = "X-Correlation-ID", required = false)
          @Size(max = 64) @Pattern(regexp = "(?s).*\\S.*") String correlationHeader) {
    return customerService.changeStatus(publicId, request);
  }

  @DeleteMapping("/{publicId}")
  public ResponseEntity<Void> delete(
      @PathVariable @Pattern(regexp = "CUS-\\d+") String publicId,
      @RequestParam @PositiveOrZero long version,
      @RequestHeader(value = "X-Correlation-ID", required = false)
          @Size(max = 64) @Pattern(regexp = "(?s).*\\S.*") String correlationHeader) {
    customerService.delete(publicId, version);
    return ResponseEntity.noContent().build();
  }
}
