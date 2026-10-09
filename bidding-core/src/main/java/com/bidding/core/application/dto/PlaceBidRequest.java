package com.bidding.core.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PlaceBidRequest(
        @NotNull(message = "Bidder ID is required")
        UUID bidderId,

        @NotNull(message = "Bid amount is required")
        @DecimalMin(value = "0.01", message = "Bid amount must be greater than zero")
        BigDecimal amount
) {
}
