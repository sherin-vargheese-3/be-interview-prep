package com.edstem.interviewprep.exception;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException() {
    super("User was not found");
  }
}
