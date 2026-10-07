package com.edstem.interviewprep.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Whitelist of sortable fields. Sorting by "any field" means any field of the product, not any
 * string: unknown names get a 400 instead of a persistence error.
 */
public enum ProductSortField {
  ID("id"),
  NAME("name"),
  CATEGORY("category"),
  PRICE("price"),
  STOCK("stock"),
  RATING("rating"),
  CREATED_AT("createdAt");

  private final String property;

  ProductSortField(String property) {
    this.property = property;
  }

  public String property() {
    return property;
  }

  public static Optional<ProductSortField> fromProperty(String property) {
    return Arrays.stream(values()).filter(f -> f.property.equals(property)).findFirst();
  }

  public static List<String> properties() {
    return Arrays.stream(values()).map(ProductSortField::property).toList();
  }
}
