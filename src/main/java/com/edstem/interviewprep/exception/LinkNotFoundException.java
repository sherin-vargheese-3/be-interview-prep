package com.edstem.interviewprep.exception;

public class LinkNotFoundException extends RuntimeException {

  public LinkNotFoundException(String code) {
    super("Short code '" + code + "' was not found");
  }
}
