package com.edstem.interviewprep.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException(UUID id) {
    super("Order " + id + " was not found");
  }
}
