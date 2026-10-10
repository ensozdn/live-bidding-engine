package com.bidding.core.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionEndedEvent(
        UUID eventId,
        String eventType,
        UUID auctionId,
        UUID highestBidderId,
        BigDecimal finalPrice,
        Instant timestamp
) {
    public static AuctionEndedEvent of(UUID auctionId, UUID highestBidderId, BigDecimal finalPrice) {
        return new AuctionEndedEvent(
                UUID.randomUUID(),
                "AUCTION_ENDED",
                auctionId,
                highestBidderId,
                finalPrice,
                Instant.now()
        );
    }
}
