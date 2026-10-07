package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.PageResponse;
import com.edstem.interviewprep.dto.ProductRequest;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.ProductSearchCriteria;
import com.edstem.interviewprep.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

  @GetMapping("/{id}")
  public ProductResponse get(@PathVariable long id) {
    return productService.get(id);
  }

  /** Writes are ADMIN-only (see SecurityConfig). */
  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
    ProductResponse created = productService.create(request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping("/{id}")
  public ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
    return productService.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
