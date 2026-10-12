package com.northstar.crm.service;

public class CustomerConflictException extends RuntimeException {
  public CustomerConflictException(String message) {
    super(message);
  }
}
