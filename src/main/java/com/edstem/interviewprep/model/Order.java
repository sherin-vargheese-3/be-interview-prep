package com.edstem.interviewprep.model;

import com.edstem.interviewprep.enums.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The unique constraint on (customer_id, idempotency_key) is what makes retries safe: however many
 * copies of a request arrive, and however concurrently, the database accepts only one order per
 * key. Keys are scoped per customer so two customers can never collide.
 */
@Entity
@Table(
    name = "orders",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_orders_customer_idempotency_key",
            columnNames = {"customer_id", "idempotency_key"}))
public class Order {

  @Id private UUID id;

  @Column(name = "customer_id", nullable = false, length = 64)
  private String customerId;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  /** SHA-256 of the canonical request, to detect a key reused for a different request. */
  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
  @OrderColumn(name = "line_no")
  private List<OrderItem> items = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  protected Order() {}

  public Order(
      UUID id,
      String customerId,
      String idempotencyKey,
      String requestHash,
      List<OrderItem> items,
      Instant createdAt) {
    this.id = id;
    this.customerId = customerId;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.status = OrderStatus.CONFIRMED;
    this.items = new ArrayList<>(items);
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getCustomerId() {
    return customerId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public List<OrderItem> getItems() {
    return List.copyOf(items);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }
}
