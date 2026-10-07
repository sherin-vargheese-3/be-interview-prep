package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.OrderStatus;
import com.edstem.interviewprep.model.Order;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
    UUID id,
    String customerId,
    OrderStatus status,
    List<OrderItemResponse> items,
    Instant createdAt,
    Instant cancelledAt) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.getId(),
        order.getCustomerId(),
        order.getStatus(),
        order.getItems().stream().map(OrderItemResponse::from).toList(),
        order.getCreatedAt(),
        order.getCancelledAt());
  }
}
