package com.northstar.crm.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Timed-path demo only. Expects {@code Authorization: Bearer lab-demo-token}.
 * Not a production IdP. OPTIONS (CORS preflight) is skipped.
 */
@Component
@Profile("dev & !prod")
public class DemoBearerFilter extends OncePerRequestFilter {
  public static final String DEMO_TOKEN = "lab-demo-token";
  public static final String DEMO_ACTOR = "demo-agent";

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String uri = request.getRequestURI();
    return uri == null || !uri.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String auth = request.getHeader("Authorization");
    if (auth == null || !auth.equals("Bearer " + DEMO_TOKEN)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    SecurityContext context = SecurityContextHolder.createEmptyContext();

    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(DEMO_ACTOR, null, List.of()));

    SecurityContextHolder.setContext(context);

    try {
        filterChain.doFilter(request, response);
        } finally {
        SecurityContextHolder.clearContext();
    }
  }
}
