package com.bidding.core.domain.model;

import com.bidding.core.domain.exception.DomainRuleException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için korumalı boş constructor
public class Auction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "item_description", columnDefinition = "TEXT")
    private String itemDescription;

    @Column(name = "starting_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal startingPrice;

    @Column(name = "current_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "min_increment", nullable = false, precision = 19, scale = 4)
    private BigDecimal minIncrement;

    @Column(name = "highest_bidder_id")
    private UUID highestBidderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AuctionStatus status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /**
     * OPTIMISTIC LOCKING:
     * İki eşzamanlı transaction aynı anda teklif vermeye çalıştığında,
     * veri tabanına ilk commit atan kazanır; diğeri OptimisticLockException alır.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "auction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<Bid> bids = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Factory Method: Yeni bir açık artırma yaratma (Domain Construction)
    // -------------------------------------------------------------------------
    public static Auction create(String title, String itemDescription, BigDecimal startingPrice,
                                  BigDecimal minIncrement, Instant startTime, Instant endTime) {
        if (startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainRuleException("Starting price must be strictly greater than zero");
        }
        if (minIncrement.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainRuleException("Minimum increment must be strictly greater than zero");
        }
        if (endTime.isBefore(startTime)) {
            throw new DomainRuleException("End time must be after start time");
        }

        Auction auction = new Auction();
        auction.id = UUID.randomUUID();
        auction.title = Objects.requireNonNull(title, "Title cannot be null");
        auction.itemDescription = itemDescription;
        auction.startingPrice = startingPrice;
        auction.currentPrice = startingPrice;
        auction.minIncrement = minIncrement;
        auction.startTime = Objects.requireNonNull(startTime, "Start time cannot be null");
        auction.endTime = Objects.requireNonNull(endTime, "End time cannot be null");
        auction.status = AuctionStatus.CREATED;
        return auction;
    }

    // -------------------------------------------------------------------------
    // Aggregate Root Davranışları (Invariant Koruyucuları)
    // -------------------------------------------------------------------------

    /**
     * Açık artırmayı başlatır.
     */
    public void start(Instant now) {
        if (this.status != AuctionStatus.CREATED) {
            throw new DomainRuleException("Only auctions in CREATED status can be started. Current: " + this.status);
        }
        if (now.isAfter(this.endTime)) {
            throw new DomainRuleException("Cannot start an auction whose end time has already passed");
        }
        this.status = AuctionStatus.ACTIVE;
    }

    /**
     * Teklif Verme Metodu (En Kritik Invariant).
     * Dışarıdan kimse bids listesine doğrudan eleman ekleyemez!
     * Kontrollerden başarıyla geçerse yeni bir Bid yaratılır ve listeye eklenir.
     */
    public Bid placeBid(UUID bidderId, BigDecimal amount, Instant now) {
        Objects.requireNonNull(bidderId, "Bidder ID cannot be null");
        Objects.requireNonNull(amount, "Bid amount cannot be null");
        Objects.requireNonNull(now, "Current timestamp cannot be null");

        // Invariant 1: Açık artırma aktif mi?
        if (this.status != AuctionStatus.ACTIVE) {
            throw new DomainRuleException("Bids can only be placed on ACTIVE auctions. Current status: " + this.status);
        }

        // Invariant 2: Açık artırma süresi dolmuş mu?
        if (now.isAfter(this.endTime)) {
            this.status = AuctionStatus.ENDED;
            throw new DomainRuleException("Auction has already expired at " + this.endTime);
        }

        // Invariant 3: Kullanıcı kendi üstüne tekrar teklif veremez!
        if (bidderId.equals(this.highestBidderId)) {
            throw new DomainRuleException("You are already the highest bidder with " + this.currentPrice);
        }

        // Invariant 4: Teklif tutarı, minimum artış kuralını karşılıyor mu?
        // Eğer henüz hiç teklif verilmemişse ilk teklif startingPrice'a eşit veya büyük olabilir.
        // Teklif varsa: amount >= currentPrice + minIncrement olmalıdır.
        BigDecimal requiredMinimum = this.bids.isEmpty()
                ? this.startingPrice
                : this.currentPrice.add(this.minIncrement);

        if (amount.compareTo(requiredMinimum) < 0) {
            throw new DomainRuleException(String.format(
                    "Bid amount %s is too low. Minimum required bid is %s (Current: %s, Min Increment: %s)",
                    amount, requiredMinimum, this.currentPrice, this.minIncrement
            ));
        }

        // Invariant'lar başarıyla doğrulandı -> Aggregate State güncellenir
        this.currentPrice = amount;
        this.highestBidderId = bidderId;

        // Child Entity (Bid) Aggregate içinden türetilir
        Bid newBid = new Bid(this, bidderId, amount, now);
        this.bids.add(newBid);

        return newBid;
    }

    /**
     * Açık artırmayı sonlandırır.
     */
    public void end(Instant now) {
        if (this.status == AuctionStatus.ENDED || this.status == AuctionStatus.CANCELLED) {
            throw new DomainRuleException("Auction is already closed. Status: " + this.status);
        }
        this.status = AuctionStatus.ENDED;
    }

    /**
     * Encapsulation Koruması:
     * Dışarıya bids koleksiyonunun salt-okunur (unmodifiable) kopyasını verir.
     */
    public List<Bid> getBids() {
        return Collections.unmodifiableList(bids);
    }
}
