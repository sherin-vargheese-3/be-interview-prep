package com.edstem.interviewprep.exception;

/**
 * An update was based on an older version of the product (e.g. stock changed by an order since).
 */
public class ProductChangedException extends RuntimeException {

  public ProductChangedException(long id, long currentVersion) {
    super(
        "Product %d was changed since you read it (current version %d); reload it and retry"
            .formatted(id, currentVersion));
  }
}
