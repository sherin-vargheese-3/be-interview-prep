package com.edstem.interviewprep.dto;

/** {@code replayed} is true when the request was a retry and the original order is returned. */
public record PlaceOrderResult(OrderResponse order, boolean replayed) {}
