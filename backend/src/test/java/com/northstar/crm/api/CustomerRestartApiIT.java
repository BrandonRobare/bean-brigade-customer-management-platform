package com.northstar.crm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.northstar.crm.CrmApplication;
import com.northstar.crm.api.dto.ChangeCustomerStatusRequest;
import com.northstar.crm.api.dto.CreateCustomerRequest;
import com.northstar.crm.api.dto.CreateInteractionRequest;
import com.northstar.crm.api.dto.UpdateCustomerRequest;
import com.northstar.crm.api.dto.UpdateInteractionRequest;
import com.northstar.crm.service.CustomerService;
import com.northstar.crm.service.InteractionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class CustomerRestartApiIT {
  @Test
  void committedCustomerAndInteractionMutationsSurviveApiRestart() throws Exception {
    String kept = null;
    String deleted = null;
    UUID interaction;
    UUID removed;
    try {
      try (var api = start()) {
        var customers = api.getBean(CustomerService.class);
        var interactions = api.getBean(InteractionService.class);
        kept = customers.create(new CreateCustomerRequest("Before restart")).publicId();
        deleted = customers.create(new CreateCustomerRequest("Delete before restart")).publicId();
        customers.update(kept, new UpdateCustomerRequest("After restart", 0L));
        customers.changeStatus(kept, new ChangeCustomerStatusRequest("CLOSED", 1L));
        interaction = interactions.create(new CreateInteractionRequest(kept, "NOTE", "Original", "restart-test"), null, "admin").id();
        removed = interactions.create(new CreateInteractionRequest(kept, "NOTE", "Removed", "restart-test"), null, "admin").id();
        interactions.update(interaction, new UpdateInteractionRequest(0L, "CALL", "Updated"));
        interactions.delete(removed, 0L);
        customers.delete(deleted, 0L);
      }
      try (var api = start()) {
        var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) api).apply(springSecurity()).build();
        var agent = jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"));
        mvc.perform(get("/api/v1/customers/" + kept).with(agent))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("After restart"))
            .andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/v1/customers/" + deleted).with(agent)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customers").param("query", deleted).with(agent)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/interactions").param("customerId", kept).with(agent))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(interaction.toString()))
            .andExpect(jsonPath("$[0].summary").value("Updated")).andExpect(jsonPath("$[0].version").value(1));
        var jdbc = api.getBean(JdbcTemplate.class);
        assertEquals("After restart", jdbc.queryForObject("SELECT full_name FROM customer WHERE public_id = ?", String.class, kept));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM customer WHERE public_id = ?", Integer.class, deleted));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM customer_interaction WHERE interaction_id = ?", Integer.class, removed));
      }
    } finally {
      var jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:postgresql://localhost:5432/crm", "crm", "change-me"));
      if (kept != null) {
        jdbc.update("DELETE FROM customer_interaction WHERE customer_id = (SELECT customer_id FROM customer WHERE public_id = ?)", kept);
        jdbc.update("DELETE FROM customer WHERE public_id = ?", kept);
      }
      if (deleted != null) jdbc.update("DELETE FROM customer WHERE public_id = ?", deleted);
    }
  }

  private ConfigurableApplicationContext start() {
    return new SpringApplicationBuilder(CrmApplication.class).profiles("test")
        .run("--server.port=0", "--spring.datasource.url=jdbc:postgresql://localhost:5432/crm",
            "--spring.datasource.username=crm", "--spring.datasource.password=change-me",
            "--spring.kafka.listener.auto-startup=false");
  }
}
