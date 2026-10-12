package com.northstar.crm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northstar.crm.domain.InteractionEntity;
import com.northstar.crm.messaging.CustomerInteractionRecordedV1;
import com.northstar.crm.repo.CustomerRepository;
import com.northstar.crm.repo.InteractionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = CustomerInteractionRecordedV1.TOPIC)
@ExtendWith(OutputCaptureExtension.class)
class InteractionMutationApiIT {
  private static final RequestPostProcessor AGENT = jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"));
  private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  private static final RequestPostProcessor VIEWER = jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"));

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/crm");
    registry.add("spring.datasource.username", () -> "crm");
    registry.add("spring.datasource.password", () -> "change-me");
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper json;
  @MockitoSpyBean InteractionRepository interactions;
  @Autowired CustomerRepository customers;
  @Autowired JdbcTemplate jdbc;
  @Autowired EmbeddedKafkaBroker broker;
  @PersistenceContext EntityManager entityManager;
  private final List<UUID> ids = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    interactions.deleteAllById(ids);
  }

  @Test
  void edit_preservesCreationFieldsAndRefetchesUpdatedVersion() throws Exception {
    UUID id = interaction();
    Map<String, Object> before = row(id);
    mockMvc.perform(edit(id.toString(), "{\"version\":0,\"interactionType\":\"EMAIL\",\"summary\":\"Updated note\"}").with(AGENT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.customerId").value("CUS-1001"))
        .andExpect(jsonPath("$.interactionType").value("EMAIL"))
        .andExpect(jsonPath("$.summary").value("Updated note"))
        .andExpect(jsonPath("$.correlationId").value("creation-correlation"))
        .andExpect(jsonPath("$.version").value(1));
    Map<String, Object> after = row(id);
    assertEquals(before.get("customer_id"), after.get("customer_id"));
    assertEquals(before.get("created_at"), after.get("created_at"));
    assertEquals(before.get("correlation_id"), after.get("correlation_id"));
    assertEquals("EMAIL", after.get("interaction_type"));
    assertEquals("Updated note", after.get("summary"));
    assertEquals(1L, after.get("version"));
    mockMvc.perform(get("/api/v1/interactions").param("customerId", "CUS-1001").with(AGENT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + id + "')].summary").value("Updated note"))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')].version").value(1));
  }

  @Test
  void partialAndSameValueEdits_incrementVersion() throws Exception {
    UUID id = interaction();
    mockMvc.perform(edit(id.toString(), "{\"version\":0,\"interactionType\":\"CALL\"}").with(ADMIN))
        .andExpect(status().isOk()).andExpect(jsonPath("$.summary").value("Original note"))
        .andExpect(jsonPath("$.version").value(1));
    mockMvc.perform(edit(id.toString(), "{\"version\":1,\"summary\":\"x\"}").with(AGENT))
        .andExpect(status().isOk()).andExpect(jsonPath("$.interactionType").value("CALL"))
        .andExpect(jsonPath("$.version").value(2));
    mockMvc.perform(edit(id.toString(), "{\"version\":2,\"summary\":\"x\"}").with(AGENT))
        .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
    assertEquals(3L, row(id).get("version"));
  }

  @Test
  void delete_removesOnlySelectedInteraction() throws Exception {
    UUID id = interaction();
    UUID other = interaction();
    Map<String, Object> otherBefore = row(other);
    mockMvc.perform(remove(id.toString(), "0").with(ADMIN))
        .andExpect(status().isNoContent()).andExpect(content().string(""));
    assertFalse(interactions.existsById(id));
    assertEquals(otherBefore, row(other));
    assertTrue(customers.existsByPublicId("CUS-1001"));
    mockMvc.perform(get("/api/v1/interactions").param("customerId", "CUS-1001").with(AGENT))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
  }

  @Test
  void staleEditAndDelete_preserveNewerRow() throws Exception {
    UUID id = interaction();
    mockMvc.perform(edit(id.toString(), "{\"version\":0,\"summary\":\"Newer note\"}").with(AGENT))
        .andExpect(status().isOk());
    Map<String, Object> newer = row(id);
    problem(edit(id.toString(), "{\"version\":0,\"summary\":\"Stale note\"}").with(AGENT), 409);
    assertEquals(newer, row(id));
    problem(remove(id.toString(), "0").with(ADMIN), 409);
    assertEquals(newer, row(id));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PATCH", "PATCH_NOOP", "DELETE"})
  void concurrentMutation_preservesWriteCommittedAfterItsVersionCheck(String method) throws Exception {
    UUID id = interaction();
    CountDownLatch loaded = new CountDownLatch(1);
    CountDownLatch proceed = new CountDownLatch(1);
    AtomicBoolean first = new AtomicBoolean(true);
    doAnswer(invocation -> {
      var result = Optional.ofNullable(entityManager.find(InteractionEntity.class, id));
      if (first.compareAndSet(true, false)) {
        loaded.countDown();
        assertTrue(proceed.await(10, TimeUnit.SECONDS));
      }
      return result;
    }).when(interactions).findById(id);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var stale = executor.submit(() -> {
        MockHttpServletRequestBuilder request = switch (method) {
          case "PATCH" -> edit(id.toString(), "{\"version\":0,\"summary\":\"Stale note\"}").with(AGENT);
          case "PATCH_NOOP" -> edit(id.toString(), "{\"version\":0,\"summary\":\"Original note\"}").with(AGENT);
          default -> remove(id.toString(), "0").with(ADMIN);
        };
        problem(request, 409);
        return null;
      });
      try {
        if (!loaded.await(10, TimeUnit.SECONDS)) {
          stale.get(1, TimeUnit.SECONDS);
          throw new AssertionError("Mutation did not load the interaction");
        }
        mockMvc.perform(edit(id.toString(), "{\"version\":0,\"summary\":\"Concurrent note\"}").with(AGENT))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
      } finally {
        proceed.countDown();
      }
      stale.get(10, TimeUnit.SECONDS);
    }
    assertEquals("Concurrent note", row(id).get("summary"));
    assertEquals(1L, row(id).get("version"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}", "{\"version\":0}", "{\"summary\":\"x\"}",
      "{\"version\":null,\"summary\":\"x\"}", "{\"version\":-1,\"summary\":\"x\"}",
      "{\"version\":0.5,\"summary\":\"x\"}", "{\"version\":\"0\",\"summary\":\"x\"}",
      "{\"version\":0,\"summary\":123}", "{\"version\":0,\"interactionType\":{},\"summary\":\"x\"}",
      "{\"version\":0,\"interactionType\":\"NOTE\",\"summary\":null}", "{\"version\":\"bad\",\"summary\":\"x\"}",
      "{\"version\":9223372036854775808,\"summary\":\"x\"}",
      "{\"version\":0,\"interactionType\":\"CHAT\"}", "{\"version\":0,\"interactionType\":\"\"}",
      "{\"version\":0,\"interactionType\":null}", "{\"version\":0,\"summary\":null}",
      "{\"version\":0,\"summary\":\"\"}", "{\"version\":0,\"summary\":\"  \\n  \"}", "not-json"
  })
  void invalidEdit_preservesRow(String body) throws Exception {
    UUID id = interaction();
    Map<String, Object> before = row(id);
    problem(edit(id.toString(), body).with(AGENT), 400);
    assertEquals(before, row(id));
  }

  @Test
  void oversizedSummary_preservesRowAndDoesNotLogText(CapturedOutput output) throws Exception {
    UUID id = interaction();
    Map<String, Object> before = row(id);
    String summary = "private-marker-".repeat(80);
    problem(edit(id.toString(), json.writeValueAsString(Map.of("version", 0, "summary", summary))).with(AGENT), 400);
    assertEquals(before, row(id));
    assertFalse(output.getAll().contains(summary));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "-1", "0.5", "bad", "9223372036854775808"})
  void invalidDeleteVersion_preservesRow(String version) throws Exception {
    UUID id = interaction();
    Map<String, Object> before = row(id);
    problem(remove(id.toString(), version).with(ADMIN), 400);
    assertEquals(before, row(id));
  }

  @Test
  void invalidUuidAndUnknownId_preserveRows() throws Exception {
    UUID id = interaction();
    Map<String, Object> before = row(id);
    for (String invalid : new String[] {"bad", "1-1-1-1-1"}) {
      problem(edit(invalid, "{\"version\":0,\"summary\":\"x\"}").with(AGENT), 400);
      problem(remove(invalid, "0").with(ADMIN), 400);
    }
    String unknown = UUID.randomUUID().toString();
    problem(edit(unknown, "{\"version\":0,\"summary\":\"x\"}").with(AGENT), 404);
    problem(remove(unknown, "0").with(ADMIN), 404);
    assertEquals(before, row(id));
  }

  @Test
  void unauthorizedAndForbiddenMutations_preserveRow() throws Exception {
    UUID id = interaction();
    Map<String, Object> before = row(id);
    problem(edit(id.toString(), "{\"version\":0,\"summary\":\"x\"}"), 401);
    problem(remove(id.toString(), "0"), 401);
    problem(edit(id.toString(), "{\"version\":0,\"summary\":\"x\"}").with(VIEWER), 403);
    problem(remove(id.toString(), "0").with(AGENT), 403);
    assertEquals(before, row(id));
  }

  @Test
  void maximumSummaryAndNonblankCorrelationHeader_areAccepted(CapturedOutput output) throws Exception {
    UUID id = interaction();
    String summary = "x".repeat(1024);
    mockMvc.perform(edit(id.toString(), json.writeValueAsString(Map.of("version", 0, "summary", summary)))
            .with(AGENT).header("X-Correlation-ID", "mutation request"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    assertEquals(summary, row(id).get("summary"));
    assertTrue(output.getAll().contains("correlationId=mutation request"));
    assertFalse(output.getAll().contains(summary));
  }

  @Test
  void invalidCorrelationHeaders_preserveRow() throws Exception {
    UUID id = interaction();
    Map<String, Object> before = row(id);
    for (String correlation : new String[] {" ", "x".repeat(65)}) {
      mockMvc.perform(edit(id.toString(), "{\"version\":0,\"summary\":\"x\"}").with(AGENT)
              .header("X-Correlation-ID", correlation)).andExpect(status().isBadRequest());
      mockMvc.perform(remove(id.toString(), "0").with(ADMIN)
              .header("X-Correlation-ID", correlation)).andExpect(status().isBadRequest());
      assertEquals(before, row(id));
    }
  }

  @Test
  void createPublishesButMutationsOnlyLogRequestCorrelation(CapturedOutput output) throws Exception {
    Map<String, Object> properties = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker);
    try (Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(properties,
        new StringDeserializer(), new ByteArrayDeserializer()).createConsumer()) {
      broker.consumeFromAnEmbeddedTopic(consumer, true, CustomerInteractionRecordedV1.TOPIC);
      String response = mockMvc.perform(post("/api/v1/interactions").with(AGENT)
              .contentType(MediaType.APPLICATION_JSON).header("X-Correlation-ID", "creation-correlation")
              .content("{\"customerId\":\"CUS-1001\",\"interactionType\":\"NOTE\",\"summary\":\"Original note\"}"))
          .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0))
          .andReturn().getResponse().getContentAsString();
      JsonNode created = json.readTree(response);
      UUID id = UUID.fromString(created.get("id").asText());
      ids.add(id);
      var event = KafkaTestUtils.getSingleRecord(consumer, CustomerInteractionRecordedV1.TOPIC, Duration.ofSeconds(10));
      assertEquals(id.toString(), json.readTree(event.value()).get("interactionId").asText());
      String summary = "synthetic-private-mutation-text";
      mockMvc.perform(edit(id.toString(), json.writeValueAsString(Map.of("version", 0, "summary", summary)))
              .with(AGENT).header("X-Correlation-ID", "edit-request-correlation"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.correlationId").value("creation-correlation"));
      problem(edit(id.toString(), "{\"version\":0,\"summary\":\"Rejected edit\"}").with(AGENT), 409);
      problem(remove(id.toString(), "0").with(ADMIN), 409);
      problem(edit(id.toString(), "{\"version\":1,\"summary\":\"\"}").with(AGENT), 400);
      mockMvc.perform(remove(id.toString(), "1").with(ADMIN).header("X-Correlation-ID", "delete-request-correlation"))
          .andExpect(status().isNoContent());
      assertTrue(KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1)).isEmpty());
      assertTrue(output.getAll().contains("edit-request-correlation"));
      assertTrue(output.getAll().contains("delete-request-correlation"));
      assertFalse(output.getAll().contains(summary));
    }
  }

  private UUID interaction() {
    InteractionEntity interaction = new InteractionEntity();
    interaction.setId(UUID.randomUUID());
    interaction.setCustomer(customers.findByPublicId("CUS-1001").orElseThrow());
    interaction.setInteractionType("NOTE");
    interaction.setSummary("Original note");
    interaction.setCorrelationId("creation-correlation");
    ids.add(interaction.getId());
    return interactions.saveAndFlush(interaction).getId();
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap("SELECT * FROM customer_interaction WHERE interaction_id = ?", id);
  }

  private MockHttpServletRequestBuilder edit(String id, String body) {
    return patch("/api/v1/interactions/{id}", id).contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private MockHttpServletRequestBuilder remove(String id, String version) {
    MockHttpServletRequestBuilder request = delete("/api/v1/interactions/{id}", id);
    return version.isEmpty() ? request : request.param("version", version);
  }

  private void problem(MockHttpServletRequestBuilder request, int code) throws Exception {
    mockMvc.perform(request.header("X-Correlation-ID", "mutation-request")).andExpect(status().is(code))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(code))
        .andExpect(jsonPath("$.correlationId").value("mutation-request"));
  }
}
