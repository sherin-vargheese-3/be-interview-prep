package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.model.OrderItem;

public record OrderItemResponse(Long productId, int quantity) {

  public static OrderItemResponse from(OrderItem item) {
    return new OrderItemResponse(item.getProductId(), item.getQuantity());
  }
}
