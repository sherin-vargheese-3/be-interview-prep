package com.edstem.interviewprep.exception;

public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(long id) {
    super("Product " + id + " was not found");
  }
}
