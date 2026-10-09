package com.northstar.crm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev-only login that mirrors the prod contract and returns the demo bearer token. */
@RestController
@Profile("dev & !prod")
@RequestMapping("/api/v1/auth")
public class DevAuthController {
  private final String username;
  private final String password;

  public DevAuthController(@Value("${crm.dev-login.username:agent1}") String username,
      @Value("${crm.dev-login.password:agent1-dev}") String password) {
    this.username = username;
    this.password = password;
  }

  public record Login(@NotBlank @Size(max = 80) String username,
                      @NotBlank @Size(max = 256) String password) {}
  public record Token(String accessToken, String tokenType, int expiresIn) {}

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Void> invalidLogin() {
    return ResponseEntity.badRequest().build();
  }

  @PostMapping("/login")
  public ResponseEntity<Token> login(@Valid @RequestBody Login login) {
    if (!username.equals(login.username()) || !password.equals(login.password())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new Token(DemoBearerFilter.DEMO_TOKEN, "Bearer", 1800));
  }
}
