package com.edstem.interviewprep.exception;

public class IdempotencyKeyInProgressException extends RuntimeException {

  public IdempotencyKeyInProgressException() {
    super("A request with this Idempotency-Key is still being processed; retry shortly");
  }
}
