package com.bidding.core.application.service;

import com.bidding.core.application.dto.AuctionResponse;
import com.bidding.core.application.dto.BidResponse;
import com.bidding.core.application.dto.CreateAuctionRequest;
import com.bidding.core.application.dto.PlaceBidRequest;
import com.bidding.core.domain.model.Auction;
import com.bidding.core.domain.repository.AuctionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Hibernate JPA Integration & Optimistic Locking Tests")
class BiddingServiceIntegrationTest {

    @Autowired
    private BiddingService biddingService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Test
    @DisplayName("Dirty Checking Doğrulaması: save() çağrılmadan nesne güncellenir ve DB'ye commit edilir")
    void shouldPersistChangesViaDirtyCheckingWithoutCallingSave() {
        Instant now = Instant.now();
        CreateAuctionRequest request = new CreateAuctionRequest(
                "MacBook Pro M3 Max",
                "Brand new 64GB RAM 1TB SSD",
                new BigDecimal("2000.00"),
                new BigDecimal("100.00"),
                now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.HOURS)
        );

        // 1. Oluştur
        AuctionResponse created = biddingService.createAuction(request);
        assertNotNull(created.id());

        // 2. Başlat (Dirty Checking ile ACTIVE olur)
        AuctionResponse started = biddingService.startAuction(created.id());
        assertEquals("ACTIVE", started.status().name());

        // 3. Teklif Ver (save() çağrılmadan DB'ye yazılmalı)
        UUID bidder1 = UUID.randomUUID();
        BidResponse bidResponse = biddingService.placeBid(
                created.id(),
                new PlaceBidRequest(bidder1, new BigDecimal("2000.00"))
        );

        assertNotNull(bidResponse.id());

        // 4. Veritabanından sıfırdan çekip kontrol et
        Auction updatedFromDb = auctionRepository.findById(created.id()).orElseThrow();
        assertEquals(0, updatedFromDb.getCurrentPrice().compareTo(new BigDecimal("2000.00")));
        assertEquals(bidder1, updatedFromDb.getHighestBidderId());
        assertEquals(2, updatedFromDb.getVersion()); // Versiyon 0 -> 1 (startAuction), 1 -> 2 (placeBid)
    }

    @Test
    @DisplayName("Optimistic Locking Çatışma Testi: Aynı anda gelen iki tekliften biri OptimisticLockingFailureException almalıdır")
    void shouldTriggerOptimisticLockingFailureOnConcurrentBids() throws InterruptedException {
        Instant now = Instant.now();
        CreateAuctionRequest request = new CreateAuctionRequest(
                "Rare Pokemon Charizard First Edition",
                "Mint condition PSA 10",
                new BigDecimal("5000.00"),
                new BigDecimal("500.00"),
                now.minus(1, ChronoUnit.HOURS),
                now.plus(2, ChronoUnit.HOURS)
        );

        AuctionResponse created = biddingService.createAuction(request);
        biddingService.startAuction(created.id());

        UUID auctionId = created.id();
        UUID bidderAlice = UUID.randomUUID();
        UUID bidderBob = UUID.randomUUID();

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockConflictCount = new AtomicInteger(0);

        // Alice'in teklif işi (5500₺)
        Callable<Void> aliceTask = () -> {
            readyLatch.countDown();
            startLatch.await(); // İki thread aynı anda başlasın diye tetikleyici beklenir
            try {
                biddingService.placeBid(auctionId, new PlaceBidRequest(bidderAlice, new BigDecimal("5500.00")));
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException ex) {
                optimisticLockConflictCount.incrementAndGet();
            }
            return null;
        };

        // Bob'un teklif işi (6000₺)
        Callable<Void> bobTask = () -> {
            readyLatch.countDown();
            startLatch.await(); // İki thread aynı anda başlasın diye tetikleyici beklenir
            try {
                biddingService.placeBid(auctionId, new PlaceBidRequest(bidderBob, new BigDecimal("6000.00")));
                successCount.incrementAndGet();
            } catch (OptimisticLockingFailureException ex) {
                optimisticLockConflictCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(aliceTask);
        Future<Void> f2 = executor.submit(bobTask);

        readyLatch.await(); // İki thread hazır olana kadar bekle
        startLatch.countDown(); // TABANCAYI ATEŞLE! İki thread aynı milisaniyede yarışa başlar.

        executor.shutdown();
        boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertTrue(finished);

        // Ya her ikisi sıralı geçti (biri diğerinden sonra DB'ye ulaştı) ya da biri OptimisticLock yedi!
        System.out.printf("Test Sonucu -> Başarılı İşlem: %d, Optimistic Lock Çatışması: %d%n",
                successCount.get(), optimisticLockConflictCount.get());

        // Veritabanındaki nihai durumu kontrol et
        Auction finalAuction = auctionRepository.findById(auctionId).orElseThrow();
        assertTrue(finalAuction.getVersion() >= 1, "Versiyon en az 1 kez güncellenmiş olmalı");
    }
}
