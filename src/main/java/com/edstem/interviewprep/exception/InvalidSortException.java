package com.edstem.interviewprep.exception;

import com.edstem.interviewprep.enums.ProductSortField;

public class InvalidSortException extends RuntimeException {

  public InvalidSortException(String property) {
    super(
        "sort field '"
            + property
            + "' is not supported; use one of "
            + ProductSortField.properties());
  }
}
