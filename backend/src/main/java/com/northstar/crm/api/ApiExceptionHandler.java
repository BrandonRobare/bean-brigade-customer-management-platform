package com.northstar.crm.api;

import com.northstar.crm.service.InvalidCustomerIdException;
import com.northstar.crm.service.UnknownCustomerException;
import com.northstar.crm.service.UnknownInteractionException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(UnknownCustomerException.class)
  public ResponseEntity<ProblemDetail> notFound(
      UnknownCustomerException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND, "Customer " + ex.getCustomerId() + " was not found", request);
  }

  @ExceptionHandler(InvalidCustomerIdException.class)
  public ResponseEntity<ProblemDetail> badRequest(
      InvalidCustomerIdException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
  }

  @ExceptionHandler(UnknownInteractionException.class)
  public ResponseEntity<ProblemDetail> interactionNotFound(
      UnknownInteractionException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "Interaction version is stale", request);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
      MissingServletRequestParameterException.class, HandlerMethodValidationException.class,
      MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ProblemDetail> invalidRequest(HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", request);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
