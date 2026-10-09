package com.bidding.core.domain.model;

import com.bidding.core.domain.exception.DomainRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Auction Aggregate Root - Domain Invariant Tests")
class AuctionAggregateTest {

    private Auction auction;
    private Instant now;
    private UUID userAlice;
    private UUID userBob;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        userAlice = UUID.randomUUID();
        userBob = UUID.randomUUID();

        // 100₺ Başlangıç, 10₺ minimum artış
        auction = Auction.create(
                "Vintage Rolex Submariner",
                "1978 original condition luxury watch",
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                now.minus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS)
        );
    }

    @Test
    @DisplayName("Invariant 1: Aktif olmayan (CREATED) açık artırmaya teklif verilemez")
    void shouldThrowExceptionWhenBiddingOnCreatedAuction() {
        assertThrows(DomainRuleException.class, () ->
                auction.placeBid(userAlice, new BigDecimal("100.00"), now)
        );
    }

    @Test
    @DisplayName("Normal Akış: Açık artırma başlatılıp geçerli teklif verildiğinde fiyat ve en yüksek teklifçi güncellenmeli")
    void shouldAcceptValidBidWhenActive() {
        auction.start(now);

        Bid bid = auction.placeBid(userAlice, new BigDecimal("100.00"), now);

        assertNotNull(bid);
        assertEquals(new BigDecimal("100.00"), auction.getCurrentPrice());
        assertEquals(userAlice, auction.getHighestBidderId());
        assertEquals(1, auction.getBids().size());
    }

    @Test
    @DisplayName("Invariant 3: Kullanıcı kendi verdiği en yüksek teklifin üzerine tekrar teklif veremez")
    void shouldThrowExceptionWhenBidderOutbidsThemselves() {
        auction.start(now);
        auction.placeBid(userAlice, new BigDecimal("100.00"), now);

        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                auction.placeBid(userAlice, new BigDecimal("120.00"), now)
        );

        assertTrue(ex.getMessage().contains("already the highest bidder"));
    }

    @Test
    @DisplayName("Invariant 4: Minimum artış miktarının altında kalan teklif reddedilmeli (100 + 10 = 110 şart)")
    void shouldRejectBidBelowMinimumIncrement() {
        auction.start(now);
        auction.placeBid(userAlice, new BigDecimal("100.00"), now);

        // Bob 105 veriyor (asgari 110 olmalıydı)
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                auction.placeBid(userBob, new BigDecimal("105.00"), now)
        );

        assertTrue(ex.getMessage().contains("too low"));
    }

    @Test
    @DisplayName("Invariant 2: Süresi dolmuş açık artırmaya teklif verilemez ve statü ENDED olur")
    void shouldRejectBidWhenAuctionIsExpired() {
        auction.start(now);
        Instant expiredTime = now.plus(3, ChronoUnit.HOURS); // endTime 2 saat sonraydı

        assertThrows(DomainRuleException.class, () ->
                auction.placeBid(userAlice, new BigDecimal("110.00"), expiredTime)
        );

        assertEquals(AuctionStatus.ENDED, auction.getStatus());
    }

    @Test
    @DisplayName("Encapsulation Koruması: bids listesi dışarıdan doğrudan manipüle edilemez")
    void shouldProtectBidsCollectionFromExternalModification() {
        auction.start(now);
        auction.placeBid(userAlice, new BigDecimal("100.00"), now);

        assertThrows(UnsupportedOperationException.class, () ->
                auction.getBids().clear()
        );
    }
}
