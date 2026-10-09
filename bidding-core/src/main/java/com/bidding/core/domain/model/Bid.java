package com.bidding.core.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bids", indexes = {
    @Index(name = "idx_bid_auction_id", columnList = "auction_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA requires no-arg constructor
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(name = "bidder_id", nullable = false)
    private UUID bidderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Package-private constructor: Bir teklif ASLA doğrudan dışarıdan 'new' ile oluşturulamaz!
     * Teklif sadece Auction Aggregate Root'u aracılığıyla yaratılabilir.
     */
    Bid(Auction auction, UUID bidderId, BigDecimal amount, Instant createdAt) {
        this.auction = Objects.requireNonNull(auction, "Auction cannot be null");
        this.bidderId = Objects.requireNonNull(bidderId, "Bidder ID cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
    }
}
