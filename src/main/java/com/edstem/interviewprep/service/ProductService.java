package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.PageResponse;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.ProductSearchCriteria;
import com.edstem.interviewprep.enums.ProductSortField;
import com.edstem.interviewprep.exception.InvalidSortException;
import com.edstem.interviewprep.repository.ProductRepository;
import com.edstem.interviewprep.repository.ProductSpecifications;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private final ProductRepository repository;

  public ProductService(ProductRepository repository) {
    this.repository = repository;
  }

  /** Filtering, sorting and paging all run in the database: one page query plus one count. */
  @Transactional(readOnly = true)
  public PageResponse<ProductResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
    Pageable safePageable =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), safeSort(pageable));
    return PageResponse.from(
        repository
            .findAll(ProductSpecifications.matching(criteria), safePageable)
            .map(ProductResponse::from));
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
