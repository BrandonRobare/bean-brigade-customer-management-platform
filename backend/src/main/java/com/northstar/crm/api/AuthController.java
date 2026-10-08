package com.northstar.crm.api;

import com.northstar.crm.security.SecurityConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("prod")
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthenticationManager authentication;
  private final JwtEncoder encoder;

  public AuthController(AuthenticationManager authentication, JwtEncoder encoder) {
    this.authentication = authentication;
    this.encoder = encoder;
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
    try {
      var user = authentication.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
      Instant now = Instant.now();
      JwtClaimsSet claims = JwtClaimsSet.builder().issuer(SecurityConfiguration.ISSUER)
          .subject(user.getName()).issuedAt(now).expiresAt(now.plusSeconds(1800))
          .claim("roles", user.getAuthorities().stream()
              .map(authority -> authority.getAuthority().substring("ROLE_".length())).toList())
          .build();
      String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Token(token, "Bearer", 1800));
    } catch (AuthenticationException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }
}
