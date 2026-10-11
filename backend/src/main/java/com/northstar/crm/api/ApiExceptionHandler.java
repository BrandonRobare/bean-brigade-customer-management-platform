package com.northstar.crm.api;

import com.northstar.crm.service.UnknownCustomerException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(UnknownCustomerException.class)
  public ResponseEntity<ProblemDetail> notFound(
      UnknownCustomerException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, "Customer " + ex.getCustomerId() + " was not found");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
