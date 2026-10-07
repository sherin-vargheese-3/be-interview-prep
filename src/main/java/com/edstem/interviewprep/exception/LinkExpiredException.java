package com.edstem.interviewprep.exception;

public class LinkExpiredException extends RuntimeException {

  public LinkExpiredException(String code) {
    super("Short code '" + code + "' has expired");
  }
}
