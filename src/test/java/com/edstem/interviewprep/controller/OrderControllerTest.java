package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.repository.OrderRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

class OrderControllerTest extends AbstractOrderApiTest {

  @Autowired OrderRepository orderRepository;
  @Autowired TransactionTemplate transactionTemplate;

  @Nested
  class Placing {

    @Test
    void validOrder_returns201AndReservesStock() throws Exception {
      long productId = createProduct(5);

      order(newKey(), "alice", productId, 2)
          .andExpect(status().isCreated())
          .andExpect(header().exists("Location"))
          .andExpect(header().string("Idempotent-Replayed", "false"))
          .andExpect(jsonPath("$.customerId").value("alice"))
          .andExpect(jsonPath("$.status").value("CONFIRMED"))
          .andExpect(jsonPath("$.items[0].productId").value(productId))
          .andExpect(jsonPath("$.items[0].quantity").value(2));

      assertThat(stockOf(productId)).isEqualTo(3);
    }

    @Test
    void insufficientStock_returns409WithClearMessage() throws Exception {
      long productId = createProduct(2);

      order(newKey(), "bob", productId, 5)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
          .andExpect(jsonPath("$.detail").value(containsString("requested 5, available 2")))
          .andExpect(jsonPath("$.productId").value(productId))
          .andExpect(jsonPath("$.requested").value(5))
          .andExpect(jsonPath("$.available").value(2));

      assertThat(stockOf(productId)).isEqualTo(2);
    }

    @Test
    void oneItemShort_rejectsWholeOrderAndReservesNothing() throws Exception {
      long plenty = createProduct(5);
      long scarce = createProduct(1);
      long ordersBefore = orderRepository.count();

      order(newKey(), "carol", plenty, 2, scarce, 2)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.productId").value(scarce));

      assertThat(stockOf(plenty)).as("first item's reservation rolled back").isEqualTo(5);
      assertThat(stockOf(scarce)).isEqualTo(1);
      assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void unknownProduct_returns404AndReservesNothing() throws Exception {
      long real = createProduct(5);

      order(newKey(), "dave", real, 1, 999_999, 1)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

      assertThat(stockOf(real)).isEqualTo(5);
    }

    @Test
    void invalidItems_return400PerField() throws Exception {
      mockMvc
          .perform(
              post(ORDERS)
                  .with(as("eve"))
                  .header("Idempotency-Key", newKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"items\": [{\"productId\": null, \"quantity\": 0}]}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors", hasSize(2)))
          .andExpect(jsonPath("$.errors[0].field").value("items[0].productId"))
          .andExpect(jsonPath("$.errors[1].field").value("items[0].quantity"))
          .andExpect(jsonPath("$.errors[1].message").value("quantity must be at least 1"));
    }

    @Test
    void notLoggedIn_returns401() throws Exception {
      mockMvc
          .perform(
              post(ORDERS)
                  .header("Idempotency-Key", newKey())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"items\": [{\"productId\": 1, \"quantity\": 1}]}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void cachedProductShowsNewStockAfterOrder() throws Exception {
      long productId = createProduct(10);
      mockMvc
          .perform(get("/api/v1/products/" + productId))
          .andExpect(jsonPath("$.stock").value(10));

      order(newKey(), "frank", productId, 3).andExpect(status().isCreated());

      mockMvc.perform(get("/api/v1/products/" + productId)).andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void databaseRejectsNegativeStockEvenIfCodeTriedIt() {
      long productId = createProduct(1);

      assertThatThrownBy(
              () ->
                  transactionTemplate.executeWithoutResult(
                      tx -> productRepository.releaseStock(productId, -5)))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(stockOf(productId)).isEqualTo(1);
    }
  }

  @Nested
  class Retries {

    @Test
    void sameKeyAndRequest_returnsOriginalOrderWith200_andReservesOnce() throws Exception {
      long productId = createProduct(10);
      String key = newKey();
      String firstId =
          read(order(key, "grace", productId, 3).andExpect(status().isCreated()))
              .get("id")
              .asText();

      order(key, "grace", productId, 3)
          .andExpect(status().isOk())
          .andExpect(header().string("Idempotent-Replayed", "true"))
          .andExpect(jsonPath("$.id").value(firstId));

      assertThat(stockOf(productId)).isEqualTo(7);
    }

    @Test
    void retryWithItemsInDifferentOrder_isStillTheSameRequest() throws Exception {
      long a = createProduct(10);
      long b = createProduct(10);
      String key = newKey();
      String firstId = read(order(key, "heidi", a, 1, b, 2)).get("id").asText();

      order(key, "heidi", b, 2, a, 1)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void sameKeyDifferentRequest_returns422AndChangesNothing() throws Exception {
      long productId = createProduct(10);
      String key = newKey();
      order(key, "ivan", productId, 1).andExpect(status().isCreated());

      order(key, "ivan", productId, 5)
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

      assertThat(stockOf(productId)).isEqualTo(9);
    }

    @Test
    void sameKeyFromDifferentCustomers_areSeparateOrders() throws Exception {
      long productId = createProduct(10);
      String key = newKey();

      String first =
          read(order(key, "judy", productId, 1).andExpect(status().isCreated())).get("id").asText();
      String second =
          read(order(key, "ken", productId, 1).andExpect(status().isCreated())).get("id").asText();

      assertThat(first).isNotEqualTo(second);
      assertThat(stockOf(productId)).isEqualTo(8);
    }

    @Test
    void missingKey_returns400() throws Exception {
      order(null, "leo", createProduct(1), 1)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
          .andExpect(jsonPath("$.errors[0].message").value("Idempotency-Key header is required"));
    }

    @Test
    void malformedKey_returns400() throws Exception {
      order("not a valid key!", "leo", createProduct(1), 1)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
    }
  }

  @Nested
  class ViewingAndCancelling {

    @Test
    void cancel_returnsStock_andIsIdempotent() throws Exception {
      long productId = createProduct(10);
      String orderId = read(order(newKey(), "mia", productId, 4)).get("id").asText();
      assertThat(stockOf(productId)).isEqualTo(6);

      mockMvc
          .perform(post(ORDERS + "/" + orderId + "/cancel").with(as("mia")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CANCELLED"))
          .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
      assertThat(stockOf(productId)).isEqualTo(10);

      mockMvc
          .perform(post(ORDERS + "/" + orderId + "/cancel").with(as("mia")))
          .andExpect(status().isOk());
      assertThat(stockOf(productId)).as("second cancel returns nothing more").isEqualTo(10);

      mockMvc
          .perform(get(ORDERS + "/" + orderId).with(as("mia")))
          .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void anotherCustomersOrder_isNotFound_andCannotBeCancelled() throws Exception {
      long productId = createProduct(10);
      String orderId = read(order(newKey(), "nina", productId, 2)).get("id").asText();

      mockMvc
          .perform(get(ORDERS + "/" + orderId).with(as("oscar")))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
      mockMvc
          .perform(post(ORDERS + "/" + orderId + "/cancel").with(as("oscar")))
          .andExpect(status().isNotFound());

      assertThat(stockOf(productId)).isEqualTo(8);
    }

    @Test
    void cancelUnknownOrder_returns404() throws Exception {
      mockMvc
          .perform(post(ORDERS + "/" + UUID.randomUUID() + "/cancel").with(as("pat")))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
  }
}
