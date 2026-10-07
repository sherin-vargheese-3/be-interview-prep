package com.edstem.interviewprep.repository;

import com.edstem.interviewprep.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
    extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

  /**
   * Check-and-decrement in one statement. The row lock taken by the UPDATE serialises concurrent
   * orders for the same product, and {@code stock >= :quantity} is evaluated against the latest
   * committed value. Returns 1 if reserved, 0 if there wasn't enough stock. The version bump makes
   * a concurrent admin edit (which read the old stock) fail its optimistic lock instead of silently
   * overwriting the reservation.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Product p set p.stock = p.stock - :quantity, p.version = p.version + 1"
          + " where p.id = :id and p.stock >= :quantity")
  int reserveStock(@Param("id") long id, @Param("quantity") int quantity);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Product p set p.stock = p.stock + :quantity, p.version = p.version + 1"
          + " where p.id = :id")
  int releaseStock(@Param("id") long id, @Param("quantity") int quantity);
}
