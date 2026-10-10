package com.bidding.core.application.service;

import com.bidding.core.domain.model.Auction;
import com.bidding.core.domain.model.AuctionStatus;
import com.bidding.core.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionLifecycleService {

    private final AuctionRepository auctionRepository;

    /**
     * SPRING PROXY & AOP DİKKATİ:
     * Bu metot ayrı bir Spring Bean'i içinde olmalıdır.
     * Eğer ScheduledThreadPool sınıfı kendi içindeki @Transactional metodu çağırırsa (self-invocation),
     * Spring CGLIB proxy'si devreye giremez ve transaction AÇILMAZ!
     * Ayrı bir Bean üzerinden çağrıldığında proxy interceptor'ı çalışır ve commit anında
     * Hibernate Dirty Checking otomatik UPDATE sorgusu atar.
     */
    @Transactional
    public void expireAuction(UUID auctionId) {
        String threadName = Thread.currentThread().getName();
        log.info("Executing auction expiration for: {} on WORKER THREAD: [{}]", auctionId, threadName);

        Auction auction = auctionRepository.findById(auctionId).orElse(null);
        if (auction == null) {
            log.warn("Auction {} not found during scheduled expiration.", auctionId);
            return;
        }

        if (auction.getStatus() == AuctionStatus.ACTIVE) {
            auction.end(Instant.now());
            log.info("Auction {} status successfully transitioned to ENDED by thread [{}] (Highest Bidder: {}, Final Price: {})",
                    auctionId, threadName, auction.getHighestBidderId(), auction.getCurrentPrice());
        } else {
            log.info("Auction {} is already in status: {}. Skipping expiration.", auctionId, auction.getStatus());
        }
    }
}
