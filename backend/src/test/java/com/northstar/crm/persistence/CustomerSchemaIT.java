package com.northstar.crm.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerSchemaIT {

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/crm");
    registry.add("spring.datasource.username", () -> "crm");
    registry.add("spring.datasource.password", () -> "change-me");
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void invalidStatus_rejected() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(
            "INSERT INTO customer (public_id, full_name, status) VALUES (?, ?, ?)",
            "CUS-T33-STATUS", "Schema Test", "INVALID"));
  }

  @Test
  void duplicatePublicId_rejected() {
    jdbc.update(
        "INSERT INTO customer (public_id, full_name, status) VALUES (?, ?, ?)",
        "CUS-T33-DUPLICATE", "Schema Test", "ACTIVE");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(
            "INSERT INTO customer (public_id, full_name, status) VALUES (?, ?, ?)",
            "CUS-T33-DUPLICATE", "Schema Test", "ACTIVE"));
  }

  @Test
  void duplicateEmailIgnoringCase_rejected() {
    jdbc.update(
        "INSERT INTO customer (public_id, full_name, status, email) VALUES (?, ?, ?, ?)",
        "CUS-T33-EMAIL-1", "Schema Test", "ACTIVE", "t33.duplicate@example.test");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(
            "INSERT INTO customer (public_id, full_name, status, email) VALUES (?, ?, ?, ?)",
            "CUS-T33-EMAIL-2", "Schema Test", "ACTIVE", "T33.DUPLICATE@EXAMPLE.TEST"));
  }

  @Test
  void malformedEmail_rejected() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(
            "INSERT INTO customer (public_id, full_name, status, email) VALUES (?, ?, ?, ?)",
            "CUS-T33-MALFORMED", "Schema Test", "ACTIVE", "bad email@example.test"));
  }

  @Test
  void nullFullName_rejected() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(
            "INSERT INTO customer (public_id, full_name, status) VALUES (?, ?, ?)",
            "CUS-T33-NULL-NAME", null, "ACTIVE"));
  }

  @Test
  void invalidInteractionType_rejected() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(
            """
            INSERT INTO customer_interaction
              (interaction_id, customer_id, interaction_type, summary, correlation_id)
            SELECT gen_random_uuid(), customer_id, 'INVALID', 'Schema test', 't33-type'
            FROM customer WHERE public_id = 'CUS-1001'
            """));
  }

  @Test
  void unknownInteractionCustomer_rejected() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbc.update(
            """
            INSERT INTO customer_interaction
              (interaction_id, customer_id, interaction_type, summary, correlation_id)
            VALUES (gen_random_uuid(), -33, 'NOTE', 'Schema test', 't33-customer')
            """));
  }

  @Test
  void validCustomer_getsUpdatedAt() {
    assertNotNull(jdbc.queryForObject(
        """
        INSERT INTO customer (public_id, full_name, status, email)
        VALUES ('CUS-T33-VALID', 'Schema Test', 'ACTIVE', 't33.valid@example.test')
        RETURNING updated_at
        """, OffsetDateTime.class));
  }

  @Test
  void nullEmails_allowedForMultipleCustomers() {
    assertEquals(2, jdbc.update(
        """
        INSERT INTO customer (public_id, full_name, status)
        VALUES ('CUS-T33-NULL-EMAIL-1', 'Schema Test', 'ACTIVE'),
               ('CUS-T33-NULL-EMAIL-2', 'Schema Test', 'PROSPECT')
        """));
  }

  @Test
  void seedEmails_backfilled() {
    assertEquals("amina.khan@example.test", jdbc.queryForObject(
        "SELECT email FROM customer WHERE public_id = 'CUS-1001'", String.class));
    assertEquals("ravi.singh@example.test", jdbc.queryForObject(
        "SELECT email FROM customer WHERE public_id = 'CUS-1002'", String.class));
  }

  @Test
  void appRole_cannotLogin() {
    assertFalse(jdbc.queryForObject(
        "SELECT rolcanlogin FROM pg_roles WHERE rolname = 'crm_app'", Boolean.class));
  }

  @Test
  void appRole_canSelectCustomers() {
    jdbc.execute("SET LOCAL ROLE crm_app");
    assertEquals("Amina Khan", jdbc.queryForObject(
        "SELECT full_name FROM customer WHERE public_id = 'CUS-1001'", String.class));
  }

  @Test
  void appRole_cannotDropCustomer() {
    jdbc.execute("SET LOCAL ROLE crm_app");
    BadSqlGrammarException error = assertThrows(
        BadSqlGrammarException.class, () -> jdbc.execute("DROP TABLE customer"));
    assertEquals("42501", error.getSQLException().getSQLState());
  }

  @Test
  void appRole_cannotCreateTable() {
    jdbc.execute("SET LOCAL ROLE crm_app");
    BadSqlGrammarException error = assertThrows(
        BadSqlGrammarException.class, () -> jdbc.execute("CREATE TABLE public.x (id BIGINT)"));
    assertEquals("42501", error.getSQLException().getSQLState());
  }
}
