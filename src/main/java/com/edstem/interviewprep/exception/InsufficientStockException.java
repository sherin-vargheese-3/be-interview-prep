package com.edstem.interviewprep.exception;

/** The whole order is rejected (and rolled back) when any one item can't be reserved. */
public class InsufficientStockException extends RuntimeException {

  private final long productId;
  private final int requested;
  private final int available;

  public InsufficientStockException(long productId, String name, int requested, int available) {
    super(
        "Insufficient stock for product %d ('%s'): requested %d, available %d"
            .formatted(productId, name, requested, available));
    this.productId = productId;
    this.requested = requested;
    this.available = available;
  }

  public long getProductId() {
    return productId;
  }

  public int getRequested() {
    return requested;
  }

  public int getAvailable() {
    return available;
  }
}
