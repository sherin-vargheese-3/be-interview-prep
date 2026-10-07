package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.PageResponse;
import com.edstem.interviewprep.dto.ProductRequest;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.ProductSearchCriteria;
import com.edstem.interviewprep.enums.ProductSortField;
import com.edstem.interviewprep.exception.InvalidFieldException;
import com.edstem.interviewprep.exception.InvalidSortException;
import com.edstem.interviewprep.exception.ProductChangedException;
import com.edstem.interviewprep.exception.ProductNotFoundException;
import com.edstem.interviewprep.model.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import com.edstem.interviewprep.repository.ProductSpecifications;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caching rules for single-product lookups (see {@code CacheConfig} for why they can't go stale):
 *
 * <ul>
 *   <li>{@link #get}: read-through, {@code sync = true} so concurrent misses for one id load it
 *       once, and a concurrent eviction waits for that load to finish.
 *   <li>{@link #update} / {@link #delete}: evict, applied <b>after</b> the transaction commits.
 *       Evicting (rather than putting the new value) avoids out-of-order puts from two concurrent
 *       updates; the next read simply reloads the committed row.
 * </ul>
 *
 * Lists are not cached: the space of filter/sort/page combinations is huge, and they are served by
 * indexed queries instead.
 */
@Service
public class ProductService {

  public static final String PRODUCT_CACHE = "products";

  private final ProductRepository repository;
  private final Clock clock;

  public ProductService(ProductRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** Filtering, sorting and paging all run in the database: one page query plus one count. */
  @Transactional(readOnly = true)
  public PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
    if (pageable.getOffset() > Integer.MAX_VALUE) {
      throw new InvalidFieldException("page", "page is too large for the requested size");
    }
    Pageable safePageable =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), safeSort(pageable));
    return PageResponse.from(
        repository
            .findAll(ProductSpecifications.matching(criteria), safePageable)
            .map(ProductResponse::from));
  }

  @Cacheable(cacheNames = PRODUCT_CACHE, key = "#id", sync = true)
  public ProductResponse get(long id) {
    return repository
        .findById(id)
        .map(ProductResponse::from)
        .orElseThrow(() -> new ProductNotFoundException(id));
  }

  @Transactional
  public ProductResponse create(ProductRequest request) {
    Product product =
        new Product(
            request.name().strip(),
            request.category(),
            request.price(),
            request.stock(),
            request.rating(),
            Instant.now(clock));
    return ProductResponse.from(repository.save(product));
  }

  @Transactional
  @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#id")
  public ProductResponse update(long id, ProductRequest request) {
    Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    if (request.version() == null) {
      throw new InvalidFieldException(
          "version", "version is required to update a product (use the value from GET)");
    }
    if (request.version() != product.getVersion()) {
      throw new ProductChangedException(id, product.getVersion());
    }
    product.update(
        request.name().strip(),
        request.category(),
        request.price(),
        request.stock(),
        request.rating());
    return ProductResponse.from(repository.saveAndFlush(product));
  }

  @Transactional
  @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#id")
  public void delete(long id) {
    if (!repository.existsById(id)) {
      throw new ProductNotFoundException(id);
    }
    repository.deleteById(id);
  }

  /**
   * Rejects unknown sort fields (400 instead of a persistence error) and appends {@code id} as a
   * tie-breaker so rows with equal values never shift between pages.
   */
  private static Sort safeSort(Pageable pageable) {
    List<Sort.Order> orders = new ArrayList<>();
    for (Sort.Order order : pageable.getSort()) {
      ProductSortField.fromProperty(order.getProperty())
          .orElseThrow(() -> new InvalidSortException(order.getProperty()));
      orders.add(order);
    }
    if (orders.stream().noneMatch(o -> o.getProperty().equals(ProductSortField.ID.property()))) {
      orders.add(Sort.Order.asc(ProductSortField.ID.property()));
    }
    return Sort.by(orders);
  }
}
