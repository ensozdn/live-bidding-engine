package com.bidding.core.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidPlacedEvent(
        UUID eventId,
        String eventType,
        UUID auctionId,
        UUID bidId,
        UUID bidderId,
        BigDecimal amount,
        Long version,
        Instant timestamp
) {
    public static BidPlacedEvent of(UUID auctionId, UUID bidId, UUID bidderId, BigDecimal amount, Long version) {
        return new BidPlacedEvent(
                UUID.randomUUID(),
                "BID_PLACED",
                auctionId,
                bidId,
                bidderId,
                amount,
                version,
                Instant.now()
        );
    }
}
