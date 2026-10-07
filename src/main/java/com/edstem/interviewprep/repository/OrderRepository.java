package com.edstem.interviewprep.repository;

import com.edstem.interviewprep.enums.OrderStatus;
import com.edstem.interviewprep.model.Order;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

  Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

  /** Orders are only visible to the customer who placed them. */
  Optional<Order> findByIdAndCustomerId(UUID id, String customerId);

  /**
   * Moves an order from one status to another only if it is still in {@code from}. Two concurrent
   * cancels can't both succeed, so stock is returned exactly once. Returns rows changed (0 or 1).
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Order o set o.status = :to, o.cancelledAt = :at"
          + " where o.id = :id and o.status = :from")
  int transition(
      @Param("id") UUID id,
      @Param("from") OrderStatus from,
      @Param("to") OrderStatus to,
      @Param("at") Instant at);
}
