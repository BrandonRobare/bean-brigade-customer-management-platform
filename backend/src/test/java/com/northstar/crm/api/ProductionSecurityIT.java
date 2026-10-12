package com.northstar.crm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northstar.crm.domain.InteractionEntity;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("prod")
class ProductionSecurityIT {
  private static final KeyPair KEYS = keys();
  @Autowired MockMvc http;
  @Autowired TestRestTemplate rest;
  @LocalManagementPort int managementPort;
  @LocalServerPort int appPort;
  @Autowired ObjectMapper json;
  @Autowired CustomerRepository customers;
  @Autowired InteractionRepository interactions;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/crm");
    properties.add("spring.datasource.username", () -> "crm");
    properties.add("spring.datasource.password", () -> "change-me");
    properties.add("crm.security.jwt-private-key", () -> pem("PRIVATE KEY", KEYS.getPrivate().getEncoded()));
    properties.add("crm.security.jwt-public-key", () -> pem("PUBLIC KEY", KEYS.getPublic().getEncoded()));
    properties.add("crm.security.agent-password", () -> "test-only-agent");
    properties.add("crm.security.admin-password", () -> "test-only-admin");
    properties.add("spring.kafka.listener.auto-startup", () -> "false");
  }

  @Test
  void anonymousAndFixedDemoTokenCannotAccessProductionApi() throws Exception {
    http.perform(get("/api/v1/interactions").param("customerId", "CUS-1001").secure(true))
        .andExpect(status().isUnauthorized());
    http.perform(get("/api/v1/interactions").param("customerId", "CUS-1001").secure(true)
            .header("Authorization", "Bearer lab-demo-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void genuineLoginGrantsApiAccessButOnlyAdminCanReadMetrics() throws Exception {
    String agent = login("agent1", "test-only-agent");
    String admin = login("admin1", "test-only-admin");
    http.perform(get("/api/v1/interactions").param("customerId", "CUS-1001").secure(true)
            .header("Authorization", "Bearer " + agent)).andExpect(status().isOk());
    assertEquals(401, management("/actuator/metrics", null).getStatusCode().value());
    assertEquals(403, management("/actuator/metrics", agent).getStatusCode().value());
    var metrics = management("/actuator/metrics", admin);
    assertEquals(200, metrics.getStatusCode().value());
    assertTrue(json.readTree(metrics.getBody()).get("names").isArray());
    http.perform(post("/api/v1/customers").secure(true).header("Authorization", "Bearer " + agent)
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    http.perform(patch("/api/v1/customers/CUS-1001/status").secure(true)
            .header("Authorization", "Bearer " + agent).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    http.perform(delete("/api/v1/interactions/00000000-0000-0000-0000-000000000001").secure(true)
            .header("Authorization", "Bearer " + agent))
        .andExpect(status().isForbidden());
    http.perform(get("/actuator/env").secure(true).header("Authorization", "Bearer " + admin))
        .andExpect(status().isForbidden());
    http.perform(get("/actuator/heapdump").secure(true).header("Authorization", "Bearer " + admin))
        .andExpect(status().isForbidden());
  }

  @Test
  void wrongCredentialsAndInvalidLoginAreRejected() throws Exception {
    http.perform(post("/api/v1/auth/login").secure(true).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"agent1\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());
    http.perform(post("/api/v1/auth/login").secure(true).contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void invalidLoginNeverLogsOrReturnsSubmittedPassword(CapturedOutput output) throws Exception {
    for (Credentials credentials : new Credentials[] {
        new Credentials("agent1", "synthetic-private-marker-".repeat(15)),
        new Credentials("x".repeat(81), "synthetic-private-marker")}) {
      String response = http.perform(post("/api/v1/auth/login").secure(true)
              .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(credentials)))
          .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
      assertFalse(response.contains(credentials.password()), "Response must not disclose the password");
      assertFalse(output.getAll().contains(credentials.password()), "Logs must not disclose the password");
    }
  }

  @Test
  void badSignatureExpiredAndWrongIssuerTokensAreRejected() throws Exception {
    for (String token : new String[] {
        token("bean-brigade-crm", Instant.now().plusSeconds(300), keys()),
        token("bean-brigade-crm", Instant.now().minusSeconds(120), KEYS),
        token("wrong-issuer", Instant.now().plusSeconds(300), KEYS)}) {
      http.perform(get("/api/v1/interactions").param("customerId", "CUS-1001").secure(true)
              .header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  @Transactional
  void interactionMutationsRejectInvalidTokensAndAgentDeletionWithoutChangingRow() throws Exception {
    InteractionEntity interaction = new InteractionEntity();
    UUID id = UUID.randomUUID();
    interaction.setId(id);
    interaction.setCustomer(customers.findByPublicId("CUS-1001").orElseThrow());
    interaction.setInteractionType("NOTE");
    interaction.setSummary("Security test note");
    interaction.setCorrelationId("security-creation");
    interactions.saveAndFlush(interaction);
    var before = jdbc.queryForMap("SELECT * FROM customer_interaction WHERE interaction_id = ?", id);
    for (String token : new String[] {
        "lab-demo-token",
        token("bean-brigade-crm", Instant.now().plusSeconds(300), keys()),
        token("bean-brigade-crm", Instant.now().minusSeconds(120), KEYS),
        token("wrong-issuer", Instant.now().plusSeconds(300), KEYS)}) {
      http.perform(patch("/api/v1/interactions/{id}", id).secure(true)
              .header("Authorization", "Bearer " + token).header("X-Correlation-ID", "security-mutation")
              .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"summary\":\"Changed\"}"))
          .andExpect(status().isUnauthorized()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.correlationId").value("security-mutation"));
      http.perform(delete("/api/v1/interactions/{id}", id).param("version", "0").secure(true)
              .header("Authorization", "Bearer " + token).header("X-Correlation-ID", "security-mutation"))
          .andExpect(status().isUnauthorized()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.correlationId").value("security-mutation"));
      assertEquals(before, jdbc.queryForMap("SELECT * FROM customer_interaction WHERE interaction_id = ?", id));
    }
    http.perform(delete("/api/v1/interactions/{id}", id).param("version", "0").secure(true)
            .header("Authorization", "Bearer " + login("agent1", "test-only-agent")))
        .andExpect(status().isForbidden()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    assertEquals(before, jdbc.queryForMap("SELECT * FROM customer_interaction WHERE interaction_id = ?", id));
  }

  @Test
  void customerWritesRejectExpiredOrInvalidSignaturesWithoutChanges() throws Exception {
    var before = jdbc.queryForMap("SELECT * FROM customer WHERE public_id = 'CUS-1002'");
    int count = jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class);
    for (String token : new String[] {
        token("bean-brigade-crm", Instant.now().plusSeconds(300), keys()),
        token("bean-brigade-crm", Instant.now().minusSeconds(120), KEYS)}) {
      for (var request : java.util.List.of(
          post("/api/v1/customers").content("{\"fullName\":\"Unauthorized\"}"),
          org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/customers/CUS-1002")
              .content("{\"fullName\":\"Unauthorized\",\"version\":0}"),
          patch("/api/v1/customers/CUS-1002/status").content("{\"status\":\"ACTIVE\",\"version\":0}"),
          delete("/api/v1/customers/CUS-1002").param("version", "0"))) {
        http.perform(request.secure(true).header("Authorization", "Bearer " + token)
                .header("X-Correlation-ID", "customer-security").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.correlationId").value("customer-security"));
      }
    }
    assertEquals(before, jdbc.queryForMap("SELECT * FROM customer WHERE public_id = 'CUS-1002'"));
    assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class));
  }

  @Test
  void loginRequiresHttpsButHealthRemainsAvailableToPlainHttpProbes() throws Exception {
    http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", "https://localhost/api/v1/auth/login"));
    http.perform(get("/readyz"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  void prometheusIsPublicOnlyOnManagementPort() throws Exception {
    var scrape = management("/actuator/prometheus", null);
    assertEquals(200, scrape.getStatusCode().value());
    assertTrue(scrape.getBody().contains("crm_kafka_messages_total"));
    for (String endpoint : new String[] {"prometheus", "env", "heapdump"}) {
      assertEquals(401, rest.getForEntity("http://localhost:" + appPort + "/actuator/" + endpoint,
          String.class).getStatusCode().value());
    }
    assertEquals(401, management("/actuator/env", null).getStatusCode().value());
    assertEquals(401, management("/actuator/heapdump", null).getStatusCode().value());
    String admin = login("admin1", "test-only-admin");
    assertEquals(403, management("/actuator/env", admin).getStatusCode().value());
    assertEquals(403, management("/actuator/heapdump", admin).getStatusCode().value());
    http.perform(get("/actuator/prometheus").secure(true).header("Authorization", "Bearer " + admin))
        .andExpect(status().isForbidden());
    http.perform(get("/actuator/metrics").secure(true).header("Authorization", "Bearer " + admin))
        .andExpect(status().isNotFound());
  }

  private ResponseEntity<String> management(String path, String token) {
    var headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return rest.exchange("http://localhost:" + managementPort + path,
        HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private String login(String user, String password) throws Exception {
    String response = http.perform(post("/api/v1/auth/login").secure(true)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new Credentials(user, password))))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(1800))
        .andReturn().getResponse().getContentAsString();
    return json.readTree(response).get("accessToken").asText();
  }

  private record Credentials(String username, String password) {}

  private static KeyPair keys() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static String pem(String label, byte[] bytes) {
    return "-----BEGIN " + label + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes)
        + "\n-----END " + label + "-----\n";
  }

  private static String token(String issuer, Instant expiry, KeyPair keys) throws Exception {
    Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
    String header = base64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
    String claims = "{\"iss\":\"" + issuer + "\",\"sub\":\"agent1\",\"roles\":[\"AGENT\"],"
        + "\"iat\":" + Instant.now().minusSeconds(180).getEpochSecond() + ",\"exp\":" + expiry.getEpochSecond() + "}";
    String content = header + "." + base64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(keys.getPrivate());
    signature.update(content.getBytes(StandardCharsets.US_ASCII));
    return content + "." + base64.encodeToString(signature.sign());
  }
}
