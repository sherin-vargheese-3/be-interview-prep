package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.dto.ProductRequest;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.enums.Category;
import com.edstem.interviewprep.service.ProductService;
import com.edstem.interviewprep.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the cache with Hibernate's own statistics: {@code getPrepareStatementCount()} is the
 * number of SQL statements actually sent to the database.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // modifies products
class ProductCachingTest extends IntegrationTest {

  private static final String PRODUCTS = "/api/v1/products";

  @Autowired ObjectMapper objectMapper;
  @Autowired ProductService productService;
  @Autowired CacheManager cacheManager;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired TransactionTemplate transactionTemplate;

  private Statistics sqlStats;

  @BeforeEach
  void resetCacheAndStats() {
    cacheManager.getCache(ProductService.PRODUCT_CACHE).clear();
    sqlStats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    sqlStats.clear();
  }

  @Test
  void repeatedLookups_queryTheDatabaseOnlyOnce() throws Exception {
    long hitsBefore = nativeCache().stats().hitCount();
    long missesBefore = nativeCache().stats().missCount();

    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(get(PRODUCTS + "/7"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(7));
    }

    assertThat(sqlStats.getPrepareStatementCount()).as("SQL statements for 5 lookups").isEqualTo(1);
    assertThat(nativeCache().stats().hitCount() - hitsBefore).isEqualTo(4);
    assertThat(nativeCache().stats().missCount() - missesBefore).isEqualTo(1);
  }

  @Test
  void cacheHitsAreVisibleThroughActuatorMetrics() throws Exception {
    double hitsBefore = actuatorCacheHits();

    mockMvc.perform(get(PRODUCTS + "/8")).andExpect(status().isOk()); // miss, loads
    mockMvc.perform(get(PRODUCTS + "/8")).andExpect(status().isOk()); // hit

    assertThat(actuatorCacheHits() - hitsBefore).isEqualTo(1.0);
  }

  private double actuatorCacheHits() throws Exception {
    String body =
        mockMvc
            .perform(
                get("/actuator/metrics/cache.gets")
                    .with(admin())
                    .param("tag", "cache:" + ProductService.PRODUCT_CACHE)
                    .param("tag", "result:hit"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).at("/measurements/0/value").asDouble();
  }

  @Test
  void update_isVisibleOnNextLookup() throws Exception {
    long id = createProduct("Original Lamp");
    getName(id, "Original Lamp"); // now cached

    mockMvc
        .perform(
            put(PRODUCTS + "/" + id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request("Renamed Lamp"))))
        .andExpect(status().isOk());

    getName(id, "Renamed Lamp");
  }

  @Test
  void delete_isVisibleOnNextLookup() throws Exception {
    long id = createProduct("Doomed Lamp");
    getName(id, "Doomed Lamp"); // now cached

    mockMvc.perform(delete(PRODUCTS + "/" + id).with(admin())).andExpect(status().isNoContent());

    mockMvc
        .perform(get(PRODUCTS + "/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
  }

  /**
   * The race that "evict after commit" exists for: another thread reads the product while an update
   * is flushed but not yet committed, so it loads (and caches) the old row. If eviction ran before
   * commit, that old value would stay cached; because it runs after commit, it is removed.
   */
  @Test
  void readDuringUncommittedUpdate_doesNotLeaveStaleCacheEntry() throws Exception {
    long id = createProduct("Before");
    ExecutorService otherThread = Executors.newSingleThreadExecutor();
    try {
      transactionTemplate.executeWithoutResult(
          tx -> {
            productService.update(id, request("After"));
            ProductResponse seenMeanwhile = call(otherThread, () -> productService.get(id));
            assertThat(seenMeanwhile.name()).isEqualTo("Before"); // uncommitted change not visible
          });
    } finally {
      otherThread.shutdownNow();
    }

    assertThat(productService.get(id).name()).isEqualTo("After");
  }

  @Test
  void unknownProduct_returns404AndIsNotCached() throws Exception {
    mockMvc.perform(get(PRODUCTS + "/999999")).andExpect(status().isNotFound());

    assertThat(nativeCache().getIfPresent(999999L)).isNull();
  }

  private static RequestPostProcessor admin() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private long createProduct(String name) throws Exception {
    String body =
        mockMvc
            .perform(
                post(PRODUCTS)
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(request(name))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  private void getName(long id, String expected) throws Exception {
    mockMvc
        .perform(get(PRODUCTS + "/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(expected));
  }

  private static ProductRequest request(String name) {
    return new ProductRequest(
        name, Category.HOME, new BigDecimal("19.99"), 5, new BigDecimal("4.5"));
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  @SuppressWarnings("unchecked")
  private Cache<Object, Object> nativeCache() {
    var decorated =
        (TransactionAwareCacheDecorator) cacheManager.getCache(ProductService.PRODUCT_CACHE);
    return ((CaffeineCache) decorated.getTargetCache()).getNativeCache();
  }

  private static <T> T call(ExecutorService executor, java.util.concurrent.Callable<T> task) {
    try {
      return executor.submit(task).get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
