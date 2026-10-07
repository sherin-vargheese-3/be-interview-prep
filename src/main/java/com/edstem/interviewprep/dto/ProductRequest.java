package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.enums.Category;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Body for create (POST) and full update (PUT). On update, {@code version} is required: the version
 * from the GET this edit is based on. Orders change stock (and bump the version) concurrently, so a
 * full replace based on an older read would otherwise overwrite their reservations. Ignored on
 * create.
 */
public record ProductRequest(
    @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,
    @NotNull(message = "category is required") Category category,
    @NotNull(message = "price is required")
        @DecimalMin(value = "0", message = "price must be zero or more")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,
    @NotNull(message = "stock is required") @Min(value = 0, message = "stock must be zero or more")
        Integer stock,
    @NotNull(message = "rating is required")
        @DecimalMin(value = "0.0", message = "rating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "rating must be between 0 and 5")
        @Digits(integer = 1, fraction = 1, message = "rating must have at most 1 decimal place")
        BigDecimal rating,
    @PositiveOrZero(message = "version must be zero or more") Long version) {}
