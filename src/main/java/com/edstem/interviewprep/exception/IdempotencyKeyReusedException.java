package com.edstem.interviewprep.exception;

public class IdempotencyKeyReusedException extends RuntimeException {

  public IdempotencyKeyReusedException() {
    super(
        "This Idempotency-Key was already used for a different request; use a new key for a new"
            + " order");
  }
}
