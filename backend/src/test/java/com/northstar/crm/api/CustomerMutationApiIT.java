package com.northstar.crm.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;
import com.northstar.crm.domain.CustomerEntity;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerMutationApiIT {
  private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  private static final RequestPostProcessor AGENT = jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"));
  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/crm");
    registry.add("spring.datasource.username", () -> "crm");
    registry.add("spring.datasource.password", () -> "change-me");
  }
  @MockitoSpyBean CustomerRepository customers;
  @MockitoSpyBean InteractionRepository interactions;
  @PersistenceContext EntityManager entityManager;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  private final List<String> ids = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    for (String id : ids) {
      jdbc.update("DELETE FROM customer_interaction WHERE customer_id = (SELECT customer_id FROM customer WHERE public_id = ?)", id);
      jdbc.update("DELETE FROM customer WHERE public_id = ?", id);
    }
  }

  @Test
  void createGeneratesFieldsAndPersistsInSearchAndProfile() throws Exception {
    String id = create("Customer management test");
    assertTrue(id.matches("CUS-\\d+"));
    mvc.perform(get("/api/v1/customers").param("query", id).with(AGENT))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].publicId").value(id))
        .andExpect(jsonPath("$[0].version").value(0));
    mvc.perform(get("/api/v1/customers/" + id).with(AGENT))
        .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Customer management test"));
    assertEquals("PROSPECT", row(id).get("status"));
  }

  @Test
  void nameUpdatePreservesCreationFieldsAndIncrementsEvenForSameName() throws Exception {
    String id = create("Original");
    Map<String, Object> before = row(id);
    for (int version = 0; version < 2; version++) {
      mvc.perform(body(put("/api/v1/customers/" + id), Map.of("fullName", "Updated", "version", version)).with(ADMIN))
          .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(version + 1));
    }
    assertEquals(before.get("public_id"), row(id).get("public_id"));
    assertEquals(before.get("created_at"), row(id).get("created_at"));
    assertEquals(before.get("status"), row(id).get("status"));
    assertEquals("Updated", row(id).get("full_name"));
    assertEquals(2L, row(id).get("version"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PROSPECT", "ACTIVE", "CLOSED"})
  void lifecycleAllowsOnlyForwardTransitionsAndKeepsHistory(String initial) throws Exception {
    String id = create("Lifecycle");
    jdbc.update("UPDATE customer SET status = ? WHERE public_id = ?", initial, id);
    UUID interaction = UUID.randomUUID();
    jdbc.update("INSERT INTO customer_interaction(interaction_id, customer_id, interaction_type, summary, correlation_id) SELECT ?, customer_id, 'NOTE', 'History', 'test' FROM customer WHERE public_id = ?", interaction, id);
    for (String target : List.of("PROSPECT", "ACTIVE", "CLOSED")) {
      jdbc.update("UPDATE customer SET status = ?, version = 0 WHERE public_id = ?", initial, id);
      Map<String, Object> before = row(id);
      boolean allowed = initial.equals("PROSPECT") && !target.equals("PROSPECT") || initial.equals("ACTIVE") && target.equals("CLOSED");
      var request = body(patch("/api/v1/customers/" + id + "/status"), Map.of("status", target, "version", 0)).with(ADMIN);
      if (allowed) {
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target)).andExpect(jsonPath("$.version").value(1));
        assertEquals(target, row(id).get("status"));
      } else {
        problem(request, 409);
        assertEquals(before, row(id));
      }
      mvc.perform(get("/api/v1/interactions").param("customerId", id).with(AGENT))
          .andExpect(status().isOk()).andExpect(jsonPath("$[0].summary").value("History"));
    }
    Map<String, Object> before = row(id);
    problem(delete("/api/v1/customers/" + id).param("version", row(id).get("version").toString()).with(ADMIN), 409);
    assertEquals(before, row(id));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM customer_interaction WHERE interaction_id = ?", Integer.class, interaction));
  }

  @Test
  void deleteRemovesOnlyCustomerAndRefetchReturnsNotFound() throws Exception {
    String id = create("Delete");
    mvc.perform(delete("/api/v1/customers/" + id).param("version", "0").with(ADMIN)).andExpect(status().isNoContent()).andExpect(content().string(""));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM customer WHERE public_id = ?", Integer.class, id));
    mvc.perform(get("/api/v1/customers/" + id).with(AGENT)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/customers").param("query", id).with(AGENT)).andExpect(content().json("[]"));
  }

  @Test
  void staleWritesAndDeletePreserveNewerRow() throws Exception {
    String id = create("Original");
    jdbc.update("UPDATE customer SET full_name = 'Newer', version = 1 WHERE public_id = ?", id);
    Map<String, Object> before = row(id);
    for (var request : writes(id)) {
      problem(request.with(ADMIN), 409);
      assertEquals(before, row(id));
    }
  }

  @Test
  void allWritesEnforceRolesAndAuthenticationAndUnknownIds() throws Exception {
    String id = create("Authorization");
    Map<String, Object> before = row(id);
    for (var request : writes(id)) problem(request.with(AGENT), 403);
    problem(body(post("/api/v1/customers"), Map.of("fullName", "Forbidden")).with(AGENT), 403);
    for (var request : writes(id)) problem(request, 401);
    problem(body(post("/api/v1/customers"), Map.of("fullName", "Unauthorized")), 401);
    for (var request : writes(id)) problem(request.header("Authorization", "Bearer invalid"), 401);
    for (var request : writes("CUS-9999")) problem(request.with(ADMIN), 404);
    for (var request : writes("bad-id")) problem(request.with(ADMIN), 400);
    assertEquals(before, row(id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"fullName\":null}", "{\"fullName\":\" \"}", "{\"fullName\":123}", "not-json"})
  void invalidCreateChangesNothing(String payload) throws Exception {
    int before = jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class);
    problem(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(payload).with(ADMIN), 400);
    assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "-1", "0.5", "\"0\"", "9223372036854775808"})
  void invalidBodyVersionsPreserveRow(String version) throws Exception {
    String id = create("Validation");
    Map<String, Object> before = row(id);
    problem(put("/api/v1/customers/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Updated\",\"version\":" + version + "}").with(ADMIN), 400);
    problem(patch("/api/v1/customers/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"version\":" + version + "}").with(ADMIN), 400);
    assertEquals(before, row(id));
  }

  @Test
  void missingVersionsInvalidNamesStatusesAndHeadersPreserveRow() throws Exception {
    String id = create("Validation");
    Map<String, Object> before = row(id);
    for (String name : List.of("", "  ", "x".repeat(201))) {
      problem(body(put("/api/v1/customers/" + id), Map.of("fullName", name, "version", 0)).with(ADMIN), 400);
      problem(body(post("/api/v1/customers"), Map.of("fullName", name)).with(ADMIN), 400);
    }
    problem(body(put("/api/v1/customers/" + id), Map.of("fullName", "Updated")).with(ADMIN), 400);
    problem(body(patch("/api/v1/customers/" + id + "/status"), Map.of("status", "ACTIVE")).with(ADMIN), 400);
    problem(body(patch("/api/v1/customers/" + id + "/status"), Map.of("status", "SUSPENDED", "version", 0)).with(ADMIN), 400);
    for (String version : List.of("", "-1", "0.5", "bad", "9223372036854775808")) {
      var request = delete("/api/v1/customers/" + id);
      if (!version.isEmpty()) request.param("version", version);
      problem(request.with(ADMIN), 400);
    }
    for (String header : List.of(" ", "x".repeat(65))) {
      for (var request : writes(id)) problem(request.with(ADMIN).header("X-Correlation-ID", header), 400);
      problem(body(post("/api/v1/customers"), Map.of("fullName", "Invalid header")).with(ADMIN).header("X-Correlation-ID", header), 400);
    }
    assertEquals(before, row(id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PUT", "PUT_NOOP", "PATCH", "DELETE"})
  void concurrentWriteAfterVersionCheckCannotBeOverwritten(String method) throws Exception {
    String id = create("Original");
    CountDownLatch loaded = new CountDownLatch(1);
    CountDownLatch proceed = new CountDownLatch(1);
    AtomicBoolean first = new AtomicBoolean(true);
    doAnswer(invocation -> {
      var result = entityManager.createQuery("SELECT c FROM CustomerEntity c WHERE c.publicId = :id", CustomerEntity.class)
          .setParameter("id", id).getResultStream().findFirst();
      if (first.compareAndSet(true, false)) {
        loaded.countDown();
        assertTrue(proceed.await(10, TimeUnit.SECONDS));
      }
      return result;
    }).when(customers).findByPublicId(id);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var stale = executor.submit(() -> {
        var request = switch (method) {
          case "PUT", "PUT_NOOP" -> body(put("/api/v1/customers/" + id), Map.of("fullName", method.equals("PUT") ? "Stale" : "Original", "version", 0));
          case "PATCH" -> body(patch("/api/v1/customers/" + id + "/status"), Map.of("status", "ACTIVE", "version", 0));
          default -> delete("/api/v1/customers/" + id).param("version", "0");
        };
        problem(request.with(ADMIN), 409);
        return null;
      });
      try {
        assertTrue(loaded.await(10, TimeUnit.SECONDS));
        mvc.perform(body(put("/api/v1/customers/" + id), Map.of("fullName", "Concurrent", "version", 0)).with(ADMIN))
            .andExpect(status().isOk());
      } finally {
        proceed.countDown();
      }
      stale.get(10, TimeUnit.SECONDS);
    }
    assertEquals("Concurrent", row(id).get("full_name"));
    assertEquals("PROSPECT", row(id).get("status"));
    assertEquals(1L, row(id).get("version"));
  }

  @Test
  void interactionInsertedAfterHistoryCheckBlocksDeletionWithoutDataLoss() throws Exception {
    String id = create("Concurrent history");
    UUID interaction = UUID.randomUUID();
    doAnswer(invocation -> {
      try (var executor = Executors.newSingleThreadExecutor()) {
        executor.submit(() -> jdbc.update("INSERT INTO customer_interaction(interaction_id, customer_id, interaction_type, summary, correlation_id) SELECT ?, customer_id, 'NOTE', 'Concurrent history', 'test' FROM customer WHERE public_id = ?", interaction, id))
            .get(10, TimeUnit.SECONDS);
      }
      return false;
    }).when(interactions).existsByCustomer_PublicId(id);
    Map<String, Object> before = row(id);
    problem(delete("/api/v1/customers/" + id).param("version", "0").with(ADMIN), 409);
    assertEquals(before, row(id));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM customer_interaction WHERE interaction_id = ?", Integer.class, interaction));
  }

  private String create(String name) throws Exception {
    var result = mvc.perform(body(post("/api/v1/customers"), Map.of("fullName", name)).with(ADMIN))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PROSPECT"))
        .andExpect(jsonPath("$.createdAt").isString()).andExpect(jsonPath("$.version").value(0)).andReturn();
    JsonNode response = json.readTree(result.getResponse().getContentAsString());
    String id = response.get("publicId").asText();
    ids.add(id);
    assertEquals("/api/v1/customers/" + id, result.getResponse().getHeader("Location"));
    return id;
  }

  private List<MockHttpServletRequestBuilder> writes(String id) throws Exception {
    return List.of(body(put("/api/v1/customers/" + id), Map.of("fullName", "Updated", "version", 0)),
        body(patch("/api/v1/customers/" + id + "/status"), Map.of("status", "ACTIVE", "version", 0)),
        delete("/api/v1/customers/" + id).param("version", "0"));
  }

  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Map<String, Object> payload) throws Exception {
    return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
  }

  private void problem(MockHttpServletRequestBuilder request, int expected) throws Exception {
    mvc.perform(request).andExpect(status().is(expected)).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(expected)).andExpect(jsonPath("$.correlationId").isString());
  }

  private Map<String, Object> row(String id) {
    return jdbc.queryForMap("SELECT * FROM customer WHERE public_id = ?", id);
  }
}
