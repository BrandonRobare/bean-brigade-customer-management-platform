package com.northstar.crm.api;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerLookupDrill {
  private final JdbcTemplate jdbc;

  CustomerLookupDrill(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/api/v1/drill/customers")
  List<Map<String, Object>> byName(@RequestParam String name) {
    return jdbc.queryForList("select * from customer where full_name = '" + name + "'");
  }
}
