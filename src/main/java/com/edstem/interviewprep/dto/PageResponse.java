package com.edstem.interviewprep.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** A stable JSON shape for pages, independent of Spring Data's internal {@code Page} format. */
public record PageResponse<T>(List<T> content, PageMetadata page) {

  public record PageMetadata(int number, int size, long totalElements, int totalPages) {}

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        new PageMetadata(
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
  }
}
