package com.edstem.interviewprep.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The customer is the authenticated caller (token subject), never a field in the body. */
public record OrderRequest(
    @NotEmpty(message = "items must contain at least one item")
        @Size(max = 50, message = "items must contain at most 50 items")
        List<@Valid OrderItemRequest> items) {}
