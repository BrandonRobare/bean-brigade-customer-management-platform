package com.northstar.crm.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {
  public static final String ISSUER = "bean-brigade-crm";

  @Bean
  @Profile("dev & !prod")
  SecurityFilterChain development(HttpSecurity http) throws Exception {
    // ponytail: retain the starter token only until the UI's real login is integrated.
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
  }

  @Bean
  @Profile("prod")
  SecurityFilterChain production(HttpSecurity http) throws Exception {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
    authentication.setJwtGrantedAuthoritiesConverter(roles);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requiresChannel(channel -> channel.requestMatchers("/api/**", "/actuator/metrics/**").requiresSecure())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
            .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
            .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.POST, "/api/v1/customers", "/api/v1/customers/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/customers/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/v1/customers/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/customers/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/interactions/**").hasRole("ADMIN")
            .requestMatchers("/api/**").hasAnyRole("AGENT", "ADMIN")
            .anyRequest().denyAll())
        .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)))
        .build();
  }

  @Bean
  @Profile("prod")
  KeyPair signingKeys(@Value("${crm.security.jwt-private-key}") String privateValue,
      @Value("${crm.security.jwt-public-key}") String publicValue, ResourceLoader resources) throws IOException {
    try (InputStream privateInput = keyInput(privateValue, resources);
        InputStream publicInput = keyInput(publicValue, resources)) {
      RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
      RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicInput);
      Assert.notNull(privateKey, "A PKCS#8 RSA private key is required");
      Assert.notNull(publicKey, "An X.509 RSA public key is required");
      Assert.isTrue(publicKey.getModulus().bitLength() >= 2048, "RSA keys must be at least 2048 bits");
      Assert.isTrue(privateKey.getModulus().equals(publicKey.getModulus()), "RSA key pair must match");
      return new KeyPair(publicKey, privateKey);
    }
  }

  private InputStream keyInput(String value, ResourceLoader resources) throws IOException {
    Assert.hasText(value, "JWT key configuration is required");
    return value.startsWith("-----BEGIN ")
        ? new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))
        : resources.getResource(value).getInputStream();
  }

  @Bean
  @Profile("prod")
  JwtEncoder jwtEncoder(KeyPair keys) {
    RSAKey rsa = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
        .privateKey(keys.getPrivate()).build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
  }

  @Bean
  @Profile("prod")
  JwtDecoder jwtDecoder(KeyPair keys) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
    return decoder;
  }

  @Bean
  @Profile("prod")
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  @Profile("prod")
  InMemoryUserDetailsManager users(PasswordEncoder passwords,
      @Value("${crm.security.agent-password}") String agentPassword,
      @Value("${crm.security.admin-password}") String adminPassword) {
    Assert.hasText(agentPassword, "DEMO_AGENT_PASSWORD is required");
    Assert.hasText(adminPassword, "DEMO_ADMIN_PASSWORD is required");
    return new InMemoryUserDetailsManager(
        User.withUsername("agent1").password(passwords.encode(agentPassword)).roles("AGENT").build(),
        User.withUsername("admin1").password(passwords.encode(adminPassword)).roles("ADMIN").build());
  }

  @Bean
  @Profile("prod")
  AuthenticationManager authenticationManager(InMemoryUserDetailsManager users, PasswordEncoder passwords) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(passwords);
    return new ProviderManager(provider);
  }
}
