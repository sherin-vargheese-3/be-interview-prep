package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.PageResponse;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.ProductSearchCriteria;
import com.edstem.interviewprep.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  /**
   * {@code ?page=0&size=20&sort=price,desc&sort=name&category=BOOKS&minPrice=10&maxPrice=50
   * &inStock=true&name=lamp}. Every filter is optional; {@code size} is capped at 100.
   */
  @GetMapping
  public PageResponse<ProductResponse> list(
      @Valid @ModelAttribute ProductSearchCriteria criteria,
      @PageableDefault(size = 20) Pageable pageable) {
    return productService.search(criteria, pageable);
  }
}
