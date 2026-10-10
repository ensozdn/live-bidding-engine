package com.bidding.core.application.worker;

import com.bidding.core.application.dto.AuctionResponse;
import com.bidding.core.application.dto.CreateAuctionRequest;
import com.bidding.core.application.service.BiddingService;
import com.bidding.core.domain.model.Auction;
import com.bidding.core.domain.model.AuctionStatus;
import com.bidding.core.domain.repository.AuctionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DisplayName("Multithreading: ScheduledThreadPool Background Expiration Tests")
class AuctionExpirationWorkerIntegrationTest {

    @Autowired
    private BiddingService biddingService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Test
    @DisplayName("ScheduledThreadPool Doğrulaması: Süresi dolan açık artırma arka planda otomatik ENDED yapılmalı")
    void shouldAutomaticallyExpireAuctionOnScheduledThreadPoolWorker() throws InterruptedException {
        Instant now = Instant.now();
        // 800 milisaniye sonra bitecek bir açık artırma
        Instant endTime = now.plusMillis(800);

        CreateAuctionRequest request = new CreateAuctionRequest(
                "Rolex Daytona Flash Auction",
                "Flash auction ending in 800 milliseconds",
                new BigDecimal("10000.00"),
                new BigDecimal("500.00"),
                now.minus(1, ChronoUnit.MINUTES),
                endTime
        );

        // 1. Açık artırmayı oluştur ve başlat (Arka plan işçisine zamanlandı)
        AuctionResponse created = biddingService.createAuction(request);
        UUID auctionId = created.id();
        biddingService.startAuction(auctionId);

        // Başlatıldıktan hemen sonra durum ACTIVE olmalı
        Auction initialAuction = auctionRepository.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.ACTIVE, initialAuction.getStatus());

        // 2. 1200 ms bekle (ScheduledThreadPool işçisinin süresi dolup tetiklenmesi için)
        Thread.sleep(1200);

        // 3. Veritabanını kontrol et: Herhangi bir API çağrısı olmadan arka plan işçisi statüyü ENDED yapmış olmalı!
        Auction expiredAuction = auctionRepository.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.ENDED, expiredAuction.getStatus(),
                "Açık artırma ScheduledThreadPool işçisi tarafından arka planda otomatik ENDED yapılmalıydı!");
    }
}
