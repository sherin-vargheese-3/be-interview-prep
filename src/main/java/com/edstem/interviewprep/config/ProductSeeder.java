package com.edstem.interviewprep.config;

import com.edstem.interviewprep.enums.Category;
import com.edstem.interviewprep.model.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds 100 products on startup when the table is empty. A fixed random seed makes the data the
 * same on every run, which keeps demos and manual checks reproducible.
 */
@Component
public class ProductSeeder implements ApplicationRunner {

  static final int SEED_COUNT = 100;

  private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);

  private static final List<String> ADJECTIVES =
      List.of("Classic", "Premium", "Compact", "Eco", "Smart", "Deluxe", "Essential", "Pro");

  private static final Map<Category, List<String>> NOUNS =
      Map.of(
          Category.ELECTRONICS, List.of("Headphones", "Speaker", "Charger", "Keyboard", "Monitor"),
          Category.BOOKS, List.of("Cookbook", "Novel", "Atlas", "Guide", "Journal"),
          Category.CLOTHING, List.of("Jacket", "T-Shirt", "Sneakers", "Scarf", "Jeans"),
          Category.HOME, List.of("Lamp", "Blanket", "Kettle", "Mug", "Cushion"),
          Category.SPORTS, List.of("Yoga Mat", "Dumbbell", "Football", "Water Bottle", "Racket"),
          Category.TOYS, List.of("Puzzle", "Robot", "Board Game", "Kite", "Building Set"),
          Category.BEAUTY, List.of("Face Cream", "Shampoo", "Perfume", "Lip Balm", "Serum"),
          Category.GROCERY, List.of("Coffee Beans", "Green Tea", "Olive Oil", "Honey", "Granola"));

  private final ProductRepository repository;
  private final Clock clock;

  public ProductSeeder(ProductRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (repository.count() > 0) {
      return;
    }
    Random random = new Random(42);
    Category[] categories = Category.values();
    Instant now = Instant.now(clock);
    List<Product> products = new ArrayList<>(SEED_COUNT);
    for (int i = 1; i <= SEED_COUNT; i++) {
      Category category = categories[random.nextInt(categories.length)];
      List<String> nouns = NOUNS.get(category);
      String name =
          ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
              + " "
              + nouns.get(random.nextInt(nouns.size()))
              + " "
              + i;
      BigDecimal price =
          BigDecimal.valueOf(1 + random.nextDouble() * 499).setScale(2, RoundingMode.HALF_UP);
      int stock = random.nextInt(6) == 0 ? 0 : random.nextInt(200) + 1;
      BigDecimal rating =
          BigDecimal.valueOf(1 + random.nextDouble() * 4).setScale(1, RoundingMode.HALF_UP);
      Instant createdAt = now.minus(Duration.ofHours((long) (SEED_COUNT - i) * 6));
      products.add(new Product(name, category, price, stock, rating, createdAt));
    }
    repository.saveAll(products);
    log.info("Seeded {} products", products.size());
  }
}
