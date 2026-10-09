package com.bidding.core.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateAuctionRequest(
        @NotBlank(message = "Title cannot be blank")
        String title,

        String itemDescription,

        @NotNull(message = "Starting price is required")
        @DecimalMin(value = "0.01", message = "Starting price must be greater than zero")
        BigDecimal startingPrice,

        @NotNull(message = "Minimum increment is required")
        @DecimalMin(value = "0.01", message = "Minimum increment must be greater than zero")
        BigDecimal minIncrement,

        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime
) {
}
