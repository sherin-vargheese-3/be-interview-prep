package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.enums.Category;
import com.edstem.interviewprep.enums.ProductSortField;
import com.edstem.interviewprep.model.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import com.edstem.interviewprep.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Read-only tests against the 100 seeded products. The property gives this class its own Spring
 * context (and so its own database), untouched by tests that create products.
 */
@TestPropertySource(properties = "test.isolated-context=product-listing")
class ProductListingTest extends IntegrationTest {

  private static final String PRODUCTS = "/api/v1/products";

  @Autowired ObjectMapper objectMapper;
  @Autowired ProductRepository repository;

  @Nested
  class Pagination {

    @Test
    void defaults_returnFirstPageOf20WithTotals() throws Exception {
      mockMvc
          .perform(get(PRODUCTS))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(20)))
          .andExpect(jsonPath("$.page.number").value(0))
          .andExpect(jsonPath("$.page.size").value(20))
          .andExpect(jsonPath("$.page.totalElements").value(100))
          .andExpect(jsonPath("$.page.totalPages").value(5));
    }

    @Test
    void sizeAbove100_isCappedAt100() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("size", "1000"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content", hasSize(100)))
          .andExpect(jsonPath("$.page.size").value(100))
          .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void pageBeyondAddressableRange_returns400NotServerError() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("page", "2147483647").param("size", "100"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void totalPages_roundsUp() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("size", "7").param("page", "14"))
          .andExpect(jsonPath("$.page.totalPages").value(15))
          .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void pagingThroughSortWithTies_returnsEveryProductExactlyOnce() throws Exception {
      Set<Long> seen = new HashSet<>();
      for (int page = 0; page < 10; page++) {
        JsonNode body =
            fetch(
                get(PRODUCTS)
                    .param("sort", "category")
                    .param("size", "10")
                    .param("page", "" + page));
        body.get("content").forEach(p -> seen.add(p.get("id").asLong()));
      }
      assertThat(seen).hasSize(100);
    }
  }

  @Nested
  class Sorting {

    @ParameterizedTest
    @EnumSource(ProductSortField.class)
    void everyFieldSortsAscendingAndDescending(ProductSortField field) throws Exception {
      for (String direction : List.of("asc", "desc")) {
        JsonNode content =
            fetch(
                    get(PRODUCTS)
                        .param("size", "100")
                        .param("sort", field.property() + "," + direction))
                .get("content");
        List<JsonNode> values = new ArrayList<>();
        content.forEach(p -> values.add(p.get(field.property())));

        Comparator<JsonNode> order = comparatorFor(field);
        assertThat(values)
            .as("sort=%s,%s", field.property(), direction)
            .hasSize(100)
            .isSortedAccordingTo(direction.equals("asc") ? order : order.reversed());
      }
    }

    @Test
    void unknownSortField_returns400() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("sort", "password,asc"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    private Comparator<JsonNode> comparatorFor(ProductSortField field) {
      return switch (field) {
        case ID, STOCK -> Comparator.comparingLong(JsonNode::asLong);
        case PRICE, RATING -> Comparator.comparing(n -> new BigDecimal(n.asText()));
        case CREATED_AT -> Comparator.comparing(n -> Instant.parse(n.asText()));
        case NAME, CATEGORY -> Comparator.comparing(JsonNode::asText);
      };
    }
  }

  @Nested
  class Filtering {

    /**
     * Tries all 32 on/off combinations of the five filters, comparing each response with the same
     * filter applied in plain Java to every product in the database.
     */
    @Test
    void everyCombinationOfFilters_matchesInMemoryExpectation() throws Exception {
      List<Product> all = repository.findAll();
      Category category = Category.ELECTRONICS;
      BigDecimal min = new BigDecimal("50");
      BigDecimal max = new BigDecimal("400");
      String name = "O"; // upper case on purpose: search is case-insensitive

      List<Predicate<Product>> filters =
          List.of(
              p -> p.getCategory() == category,
              p -> p.getPrice().compareTo(min) >= 0,
              p -> p.getPrice().compareTo(max) <= 0,
              p -> p.getStock() > 0,
              p -> p.getName().toLowerCase(Locale.ROOT).contains("o"));
      List<Function<MockHttpServletRequestBuilder, MockHttpServletRequestBuilder>> params =
          List.of(
              r -> r.param("category", category.name()),
              r -> r.param("minPrice", min.toPlainString()),
              r -> r.param("maxPrice", max.toPlainString()),
              r -> r.param("inStock", "true"),
              r -> r.param("name", name));

      for (int mask = 0; mask < 32; mask++) {
        MockHttpServletRequestBuilder request = get(PRODUCTS).param("size", "100");
        Predicate<Product> expected = p -> true;
        for (int bit = 0; bit < 5; bit++) {
          if ((mask & (1 << bit)) != 0) {
            request = params.get(bit).apply(request);
            expected = expected.and(filters.get(bit));
          }
        }
        List<Long> expectedIds =
            all.stream().filter(expected).map(Product::getId).sorted().toList();

        JsonNode body = fetch(request);
        List<Long> actualIds = new ArrayList<>();
        body.get("content").forEach(p -> actualIds.add(p.get("id").asLong()));

        assertThat(actualIds)
            .as("filter mask %s", Integer.toBinaryString(mask))
            .isEqualTo(expectedIds);
        assertThat(body.at("/page/totalElements").asLong()).isEqualTo(expectedIds.size());
        if (mask == 31) {
          assertThat(expectedIds)
              .as("all five filters together should match something")
              .isNotEmpty();
        }
      }
    }

    @Test
    void nameSearch_treatsWildcardsLiterally() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("name", "%"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void unparseableFilters_return400WithMessagePerField() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("category", "FURNITURE").param("minPrice", "abc"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors", hasSize(2)))
          .andExpect(jsonPath("$.errors[0].field").value("category"))
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value(
                      "category must be one of [ELECTRONICS, BOOKS, CLOTHING, HOME, SPORTS, TOYS,"
                          + " BEAUTY, GROCERY]"))
          .andExpect(jsonPath("$.errors[1].field").value("minPrice"))
          .andExpect(jsonPath("$.errors[1].message").value("minPrice must be a number"));
    }

    @Test
    void outOfRangeFilters_return400WithMessagePerField() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("minPrice", "-1").param("maxPrice", "-5"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[*].field", hasItems("minPrice", "maxPrice")))
          .andExpect(jsonPath("$.errors[*].message", hasItem("maxPrice must be zero or more")));
    }

    @Test
    void minPriceAboveMaxPrice_returns400() throws Exception {
      mockMvc
          .perform(get(PRODUCTS).param("minPrice", "100").param("maxPrice", "10"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("priceRange"))
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value("maxPrice must be greater than or equal to minPrice"));
    }
  }

  @Nested
  class Access {

    @Test
    void readsArePublic() throws Exception {
      mockMvc.perform(get(PRODUCTS + "/1")).andExpect(status().isOk());
    }

    @Test
    void actuatorIndexIsNotPublic() throws Exception {
      mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
      mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void writeWithoutToken_returns401() throws Exception {
      mockMvc
          .perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void writeAsUser_returns403() throws Exception {
      mockMvc
          .perform(
              delete(PRODUCTS + "/1")
                  .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
  }

  private JsonNode fetch(MockHttpServletRequestBuilder request) throws Exception {
    String body =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }
}
