package com.edstem.interviewprep.exception;

/** A field rule checked in the service (needs stored state); reported like a validation error. */
public class InvalidFieldException extends RuntimeException {

  private final String field;

  public InvalidFieldException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
