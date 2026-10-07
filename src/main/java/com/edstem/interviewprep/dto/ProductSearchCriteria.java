package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.Category;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Optional list filters, bound from query parameters; any combination may be supplied. */
public record ProductSearchCriteria(
    Category category,
    @DecimalMin(value = "0", message = "minPrice must be zero or more") BigDecimal minPrice,
    @DecimalMin(value = "0", message = "maxPrice must be zero or more") BigDecimal maxPrice,
    Boolean inStock,
    @Size(max = 100, message = "name must be at most 100 characters") String name) {

  @AssertTrue(message = "maxPrice must be greater than or equal to minPrice")
  public boolean isPriceRange() {
    return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
  }
}
