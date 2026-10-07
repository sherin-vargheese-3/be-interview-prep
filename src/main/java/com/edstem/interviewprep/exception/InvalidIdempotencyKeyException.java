package com.edstem.interviewprep.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {

  public InvalidIdempotencyKeyException() {
    super(
        "Idempotency-Key must be 1-100 characters of letters, digits, '-', '_', ':' or '.'"
            + " (a UUID is recommended)");
  }
}
