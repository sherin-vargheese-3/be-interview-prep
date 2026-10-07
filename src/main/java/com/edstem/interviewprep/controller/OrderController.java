package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.OrderRequest;
import com.edstem.interviewprep.dto.OrderResponse;
import com.edstem.interviewprep.dto.PlaceOrderResult;
import com.edstem.interviewprep.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Requires a logged-in user (Q3); the customer is the token's subject. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

  static final String IDEMPOTENCY_KEY = "Idempotency-Key";
  static final String REPLAYED = "Idempotent-Replayed";

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  /**
   * {@code 201} for a new order. A retry with the same {@code Idempotency-Key} returns the original
   * order with {@code 200} and {@code Idempotent-Replayed: true}.
   */
  @PostMapping
  public ResponseEntity<OrderResponse> place(
      @AuthenticationPrincipal Jwt caller,
      @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
      @Valid @RequestBody OrderRequest request) {
    PlaceOrderResult result = orderService.place(caller.getSubject(), idempotencyKey, request);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(result.order().id())
            .toUri();
    return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
        .location(location)
        .header(REPLAYED, String.valueOf(result.replayed()))
        .body(result.order());
  }

  @GetMapping("/{id}")
  public OrderResponse get(@AuthenticationPrincipal Jwt caller, @PathVariable UUID id) {
    return orderService.get(caller.getSubject(), id);
  }

  /** Idempotent: cancelling twice returns the stock once. */
  @PostMapping("/{id}/cancel")
  public OrderResponse cancel(@AuthenticationPrincipal Jwt caller, @PathVariable UUID id) {
    return orderService.cancel(caller.getSubject(), id);
  }
}
