package com.northstar.crm.service;

public class InvalidCustomerIdException extends RuntimeException {
  public InvalidCustomerIdException(String customerId) {
    super("Invalid customer ID: " + customerId);
  }
}
