package com.northstar.crm.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerApiIT {

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/crm");
    registry.add("spring.datasource.username", () -> "crm");
    registry.add("spring.datasource.password", () -> "change-me");
  }

  @Autowired MockMvc mockMvc;

  private ResultActions getAuthorized(String url) throws Exception {
    return mockMvc.perform(get(url).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"))));
  }

  @Test
  void search_withoutBearer_unauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/customers").param("query", "Amina"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void search_byName_findsAmina() throws Exception {
    getAuthorized("/api/v1/customers?query=amina")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].publicId").value("CUS-1001"))
        .andExpect(jsonPath("$[0].fullName").value("Amina Khan"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  void search_byPublicId_findsAmina() throws Exception {
    getAuthorized("/api/v1/customers?query=CUS-1001")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].publicId").value("CUS-1001"));
  }

  @Test
  void search_noMatch_emptyArray() throws Exception {
    getAuthorized("/api/v1/customers?query=CUS-9999")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void profile_known_returnsCustomer() throws Exception {
    getAuthorized("/api/v1/customers/CUS-1002")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicId").value("CUS-1002"))
        .andExpect(jsonPath("$.fullName").value("Ravi Singh"))
        .andExpect(jsonPath("$.status").value("PROSPECT"))
        .andExpect(jsonPath("$.createdAt").isString())
        .andExpect(jsonPath("$.id").doesNotExist());
  }

  @Test
  void profile_unknown_notFound() throws Exception {
    getAuthorized("/api/v1/customers/CUS-9999")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not-found"));
  }
}
