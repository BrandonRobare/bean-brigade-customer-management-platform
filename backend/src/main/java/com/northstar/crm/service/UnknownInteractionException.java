package com.northstar.crm.service;

import java.util.UUID;

public class UnknownInteractionException extends RuntimeException {

  public UnknownInteractionException(UUID id) {
    super("Interaction " + id + " was not found");
  }
}
