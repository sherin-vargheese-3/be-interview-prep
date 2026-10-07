package com.edstem.interviewprep.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.edstem.interviewprep.enums.Category;
import com.edstem.interviewprep.model.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import com.edstem.interviewprep.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Each test creates its own products, so tests never share stock. Customers are authenticated
 * callers: {@link #as(String)} attaches a token whose subject is the customer id.
 */
abstract class AbstractOrderApiTest extends IntegrationTest {

  static final String ORDERS = "/api/v1/orders";

  @Autowired ObjectMapper objectMapper;
  @Autowired ProductRepository productRepository;

  long createProduct(int stock) {
    Product product =
        new Product(
            "Product " + UUID.randomUUID().toString().substring(0, 8),
            Category.HOME,
            new BigDecimal("9.99"),
            stock,
            new BigDecimal("4.0"),
            NOW);
    return productRepository.save(product).getId();
  }

  int stockOf(long productId) {
    return productRepository.findById(productId).orElseThrow().getStock();
  }

  static RequestPostProcessor as(String customerId) {
    return jwt().jwt(token -> token.subject(customerId));
  }

  /** {@code items} alternates productId, quantity: {@code order(key, "c1", a, 2, b, 1)}. */
  ResultActions order(String key, String customerId, long... items) throws Exception {
    return mockMvc.perform(orderRequest(key, customerId, items));
  }

  MockHttpServletRequestBuilder orderRequest(String key, String customerId, long... items)
      throws Exception {
    List<Map<String, Object>> lines = new ArrayList<>();
    for (int i = 0; i < items.length; i += 2) {
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("productId", items[i]);
      line.put("quantity", items[i + 1]);
      lines.add(line);
    }
    MockHttpServletRequestBuilder request =
        post(ORDERS)
            .with(as(customerId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("items", lines)));
    return key == null ? request : request.header("Idempotency-Key", key);
  }

  JsonNode read(ResultActions result) throws Exception {
    return read(result.andReturn());
  }

  JsonNode read(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  static String newKey() {
    return UUID.randomUUID().toString();
  }

  /**
   * Runs the tasks on separate threads, released at the same instant by a latch so they really
   * contend, and returns their results in order. Fails if anything hangs (e.g. a deadlock).
   */
  static <T> List<T> runSimultaneously(List<Callable<T>> tasks) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.call();
                }));
      }
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(60, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }
}
