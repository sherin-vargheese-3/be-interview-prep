package com.edstem.interviewprep.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

  public EmailAlreadyRegisteredException(String email) {
    super("Email " + email + " is already registered");
  }
}
