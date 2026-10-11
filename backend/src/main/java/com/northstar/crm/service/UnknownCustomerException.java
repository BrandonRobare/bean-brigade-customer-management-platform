package com.northstar.crm.service;

public class UnknownCustomerException extends RuntimeException {
  private final String customerId;

  public UnknownCustomerException(String customerId) {
    super("Unknown customer: " + customerId);
    this.customerId = customerId;
  }

  public String getCustomerId() {
    return customerId;
  }
}
