package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.edstem.interviewprep.enums.OrderStatus;
import com.edstem.interviewprep.repository.OrderRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Real threads hitting the full HTTP stack and database at the same instant. */
class OrderConcurrencyTest extends AbstractOrderApiTest {

  @Autowired OrderRepository orderRepository;

  /** The acceptance criterion. */
  @Test
  void fiftySimultaneousOrders_forStockOf10_exactly10Succeed_andStockEndsAtZero() throws Exception {
    long productId = createProduct(10);
    List<Callable<MvcResult>> orders = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      String customer = "customer-" + i;
      orders.add(() -> mockMvc.perform(orderRequest(newKey(), customer, productId, 1)).andReturn());
    }

    List<MvcResult> results = runSimultaneously(orders);

    Map<Integer, Long> byStatus =
        results.stream()
            .collect(
                Collectors.groupingBy(r -> r.getResponse().getStatus(), Collectors.counting()));
    assertThat(byStatus).containsOnly(Map.entry(201, 10L), Map.entry(409, 40L));
    for (MvcResult rejected : results) {
      if (rejected.getResponse().getStatus() == 409) {
        assertThat(read(rejected).get("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
      }
    }
    assertThat(stockOf(productId)).isZero();
    assertThat(confirmedUnitsOf(productId)).isEqualTo(10);
  }

  /**
   * Acceptance criterion, worst case: the retries arrive at the same time, not one after another.
   */
  @Test
  void simultaneousRetriesOfOneRequest_createExactlyOneOrder() throws Exception {
    long productId = createProduct(10);
    String key = newKey();
    List<Callable<MvcResult>> retries = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      retries.add(
          () -> mockMvc.perform(orderRequest(key, "retrying-customer", productId, 2)).andReturn());
    }

    List<MvcResult> results = runSimultaneously(retries);

    Set<String> orderIds = new HashSet<>();
    int created = 0;
    for (MvcResult result : results) {
      int status = result.getResponse().getStatus();
      assertThat(status).as("each copy is created, replayed, or told to retry").isIn(201, 200, 409);
      if (status == 201) {
        created++;
      }
      if (status == 200 || status == 201) {
        orderIds.add(read(result).get("id").asText());
      } else {
        assertThat(read(result).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_IN_PROGRESS");
      }
    }
    assertThat(created).isEqualTo(1);
    assertThat(orderIds).hasSize(1);
    assertThat(stockOf(productId)).isEqualTo(8);
    assertThat(confirmedUnitsOf(productId)).isEqualTo(2);
  }

  /**
   * Half the orders list the products as (A, B), half as (B, A). Without a consistent lock order
   * this deadlocks; with it every request completes and nothing is oversold.
   */
  @Test
  void multiItemOrdersInOppositeOrder_neitherDeadlockNorOversell() throws Exception {
    long a = createProduct(20);
    long b = createProduct(20);
    List<Callable<MvcResult>> orders = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      String customer = "multi-" + i;
      long[] items = i % 2 == 0 ? new long[] {a, 1, b, 1} : new long[] {b, 1, a, 1};
      orders.add(() -> mockMvc.perform(orderRequest(newKey(), customer, items)).andReturn());
    }

    List<MvcResult> results = runSimultaneously(orders);

    long succeeded = results.stream().filter(r -> r.getResponse().getStatus() == 201).count();
    assertThat(succeeded).isEqualTo(20);
    assertThat(stockOf(a)).isZero();
    assertThat(stockOf(b)).isZero();
  }

  @Test
  void simultaneousCancels_returnStockExactlyOnce() throws Exception {
    long productId = createProduct(10);
    String orderId = read(order(newKey(), "canceller", productId, 3)).get("id").asText();
    assertThat(stockOf(productId)).isEqualTo(7);
    List<Callable<MvcResult>> cancels = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      cancels.add(
          () ->
              mockMvc
                  .perform(post(ORDERS + "/" + orderId + "/cancel").with(as("canceller")))
                  .andReturn());
    }

    List<MvcResult> results = runSimultaneously(cancels);

    assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    assertThat(stockOf(productId)).isEqualTo(10);
  }

  private int confirmedUnitsOf(long productId) {
    return orderRepository.findAll().stream()
        .filter(o -> o.getStatus() == OrderStatus.CONFIRMED)
        .flatMap(o -> o.getItems().stream())
        .filter(item -> item.getProductId() == productId)
        .mapToInt(item -> item.getQuantity())
        .sum();
  }
}
