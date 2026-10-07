package com.edstem.interviewprep.model;

import com.edstem.interviewprep.enums.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Indexed on the columns used to filter and sort, so listing stays fast as the table grows.
 * (category, price) serves the common "category + price range" query.
 */
@Entity
@Table(
    name = "products",
    indexes = {
      @Index(name = "idx_products_category_price", columnList = "category, price"),
      @Index(name = "idx_products_price", columnList = "price"),
      @Index(name = "idx_products_stock", columnList = "stock"),
      @Index(name = "idx_products_rating", columnList = "rating"),
      @Index(name = "idx_products_created_at", columnList = "created_at"),
      @Index(name = "idx_products_name", columnList = "name")
    })
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Category category;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(nullable = false)
  private int stock;

  @Column(nullable = false, precision = 2, scale = 1)
  private BigDecimal rating;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version private long version;

  protected Product() {}

  public Product(
      String name,
      Category category,
      BigDecimal price,
      int stock,
      BigDecimal rating,
      Instant createdAt) {
    this.name = name;
    this.category = category;
    this.price = price;
    this.stock = stock;
    this.rating = rating;
    this.createdAt = createdAt;
  }

  public void update(
      String name, Category category, BigDecimal price, int stock, BigDecimal rating) {
    this.name = name;
    this.category = category;
    this.price = price;
    this.stock = stock;
    this.rating = rating;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Category getCategory() {
    return category;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public int getStock() {
    return stock;
  }

  public BigDecimal getRating() {
    return rating;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }
}
