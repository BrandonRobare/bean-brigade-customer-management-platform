package com.northstar.crm.api;

import com.northstar.crm.api.dto.CreateInteractionRequest;
import com.northstar.crm.api.dto.InteractionResponse;
import com.northstar.crm.api.dto.UpdateInteractionRequest;
import com.northstar.crm.service.InteractionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interactions")
public class InteractionController {

  private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  private final InteractionService interactionService;

  public InteractionController(InteractionService interactionService) {
    this.interactionService = interactionService;
  }

  @GetMapping
  public List<InteractionResponse> list(@RequestParam String customerId) {
    return interactionService.list(customerId);
  }

  @PostMapping
  public ResponseEntity<InteractionResponse> create(
      @Valid @RequestBody CreateInteractionRequest request,
      @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader, Principal principal) {
    InteractionResponse body = interactionService.create(request, correlationHeader, principal.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  @PatchMapping("/{id}")
  public InteractionResponse update(
      @PathVariable @Pattern(regexp = UUID_PATTERN) String id,
      @Valid @RequestBody UpdateInteractionRequest request,
      @RequestHeader(value = "X-Correlation-ID", required = false)
          @Size(max = 64) @Pattern(regexp = "(?s).*\\S.*") String correlationHeader) {
    return interactionService.update(UUID.fromString(id), request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable @Pattern(regexp = UUID_PATTERN) String id,
      @RequestParam @PositiveOrZero long version,
      @RequestHeader(value = "X-Correlation-ID", required = false)
          @Size(max = 64) @Pattern(regexp = "(?s).*\\S.*") String correlationHeader) {
    interactionService.delete(UUID.fromString(id), version);
    return ResponseEntity.noContent().build();
  }
}
