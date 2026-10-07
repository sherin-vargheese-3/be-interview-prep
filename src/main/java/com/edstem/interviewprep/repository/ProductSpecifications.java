package com.edstem.interviewprep.repository;

import com.edstem.interviewprep.dto.ProductSearchCriteria;
import com.edstem.interviewprep.enums.Category;
import com.edstem.interviewprep.model.Product;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/**
 * Each filter is an independent, optional predicate; absent filters contribute nothing, and the
 * present ones are AND-ed. That is what lets any combination work in a single query.
 */
public final class ProductSpecifications {

  private static final char ESCAPE = '\\';

  private ProductSpecifications() {}

  public static Specification<Product> matching(ProductSearchCriteria criteria) {
    return Specification.allOf(
        Stream.of(
                categoryIs(criteria.category()),
                priceAtLeast(criteria.minPrice()),
                priceAtMost(criteria.maxPrice()),
                Boolean.TRUE.equals(criteria.inStock()) ? inStock() : null,
                nameContains(criteria.name()))
            .filter(Objects::nonNull)
            .toList());
  }

  static Specification<Product> categoryIs(Category category) {
    return category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category);
  }

  static Specification<Product> priceAtLeast(BigDecimal min) {
    return min == null
        ? null
        : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
  }

  static Specification<Product> priceAtMost(BigDecimal max) {
    return max == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
  }

  static Specification<Product> inStock() {
    return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
  }

  /** Case-insensitive "contains"; user input is escaped so % and _ match literally. */
  static Specification<Product> nameContains(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    String pattern = "%" + escapeLike(search.strip().toLowerCase(Locale.ROOT)) + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, ESCAPE);
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
