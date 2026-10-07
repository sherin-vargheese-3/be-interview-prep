package com.edstem.interviewprep.service;

import com.edstem.interviewprep.dto.OrderItemRequest;
import com.edstem.interviewprep.dto.OrderRequest;
import com.edstem.interviewprep.dto.OrderResponse;
import com.edstem.interviewprep.dto.PlaceOrderResult;
import com.edstem.interviewprep.exception.IdempotencyKeyInProgressException;
import com.edstem.interviewprep.exception.IdempotencyKeyReusedException;
import com.edstem.interviewprep.exception.InsufficientStockException;
import com.edstem.interviewprep.exception.InvalidIdempotencyKeyException;
import com.edstem.interviewprep.exception.ProductNotFoundException;
import com.edstem.interviewprep.model.Order;
import com.edstem.interviewprep.model.OrderItem;
import com.edstem.interviewprep.model.Product;
import com.edstem.interviewprep.repository.OrderRepository;
import com.edstem.interviewprep.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Placing an order:
 *
 * <ol>
 *   <li><b>Retry?</b> If this customer already has an order for this {@code Idempotency-Key},
 *       return it (after checking the request is the same).
 *   <li><b>One transaction</b>: insert the order (claiming the key via a unique constraint), then
 *       reserve each item with an atomic conditional UPDATE. Any shortfall throws, and the rollback
 *       undoes every reservation already made: all-or-nothing.
 *   <li><b>Concurrent retry</b>: if two copies race past step 1, the unique constraint lets only
 *       one commit. The loser's whole transaction rolls back (including its stock reservations),
 *       and it returns the winner's order.
 * </ol>
 */
@Service
public class OrderService {

  static final String IDEMPOTENCY_CONSTRAINT = "UK_ORDERS_CUSTOMER_IDEMPOTENCY_KEY";
  private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_\\-:.]{1,100}");

  private final OrderRepository orders;
  private final ProductRepository products;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public OrderService(
      OrderRepository orders,
      ProductRepository products,
      TransactionTemplate transaction,
      Clock clock) {
    this.orders = orders;
    this.products = products;
    this.transaction = transaction;
    this.clock = clock;
  }

  public PlaceOrderResult place(String customerId, String idempotencyKey, OrderRequest request) {
    String key = validateKey(idempotencyKey);
    SortedMap<Long, Integer> lines = mergeAndSort(request.items());
    String requestHash = fingerprint(customerId, lines);

    Optional<Order> previous = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
    if (previous.isPresent()) {
      return replay(previous.get(), requestHash);
    }
    try {
      Order created =
          transaction.execute(tx -> reserveAndSave(customerId, key, requestHash, lines));
      return new PlaceOrderResult(OrderResponse.from(created), false);
    } catch (DataIntegrityViolationException e) {
      if (!isIdempotencyConflict(e)) {
        throw e;
      }
      // A concurrent copy of this request committed first; its order is the answer.
      return orders
          .findByCustomerIdAndIdempotencyKey(customerId, key)
          .map(winner -> replay(winner, requestHash))
          .orElseThrow(IdempotencyKeyInProgressException::new);
    }
  }

  private Order reserveAndSave(
      String customerId, String key, String requestHash, SortedMap<Long, Integer> lines) {
    Map<Long, Product> known =
        products.findAllById(lines.keySet()).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    lines.keySet().stream()
        .filter(id -> !known.containsKey(id))
        .findFirst()
        .ifPresent(
            id -> {
              throw new ProductNotFoundException(id);
            });

    List<OrderItem> items =
        lines.entrySet().stream().map(e -> new OrderItem(e.getKey(), e.getValue())).toList();
    Order order =
        orders.saveAndFlush(
            new Order(UUID.randomUUID(), customerId, key, requestHash, items, Instant.now(clock)));

    // Ascending product id: every transaction locks rows in the same order, so two multi-item
    // orders touching the same products can't deadlock.
    for (Map.Entry<Long, Integer> line : lines.entrySet()) {
      if (products.reserveStock(line.getKey(), line.getValue()) == 0) {
        int available = products.findById(line.getKey()).map(Product::getStock).orElse(0);
        throw new InsufficientStockException(
            line.getKey(), known.get(line.getKey()).getName(), line.getValue(), available);
      }
    }
    return order;
  }

  private static PlaceOrderResult replay(Order order, String requestHash) {
    if (!order.getRequestHash().equals(requestHash)) {
      throw new IdempotencyKeyReusedException();
    }
    return new PlaceOrderResult(OrderResponse.from(order), true);
  }

  private static String validateKey(String key) {
    String trimmed = key == null ? "" : key.strip();
    if (!KEY_FORMAT.matcher(trimmed).matches()) {
      throw new InvalidIdempotencyKeyException();
    }
    return trimmed;
  }

  /** Same product listed twice becomes one line; sorted by product id (the lock order). */
  private static SortedMap<Long, Integer> mergeAndSort(List<OrderItemRequest> items) {
    SortedMap<Long, Integer> lines = new TreeMap<>();
    items.forEach(item -> lines.merge(item.productId(), item.quantity(), Integer::sum));
    return lines;
  }

  /** Canonical form of the request, so a retry with items in another order still matches. */
  private static String fingerprint(String customerId, SortedMap<Long, Integer> lines) {
    String canonical =
        customerId
            + "|"
            + lines.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }

  private static boolean isIdempotencyConflict(DataIntegrityViolationException e) {
    String message = String.valueOf(e.getMostSpecificCause().getMessage());
    return message.toUpperCase(Locale.ROOT).contains(IDEMPOTENCY_CONSTRAINT);
  }
}
