package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.Category;
import com.edstem.interviewprep.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable snapshot of a product. This, not the JPA entity, is what gets cached: an entity is
 * mutable and tied to a persistence context, a record is safe to share between threads.
 */
public record ProductResponse(
    Long id,
    String name,
    Category category,
    BigDecimal price,
    int stock,
    BigDecimal rating,
    Instant createdAt,
    long version) {

  public static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getCategory(),
        product.getPrice(),
        product.getStock(),
        product.getRating(),
        product.getCreatedAt(),
        product.getVersion());
  }
}
