package com.bidding.core.application.worker;

import com.bidding.core.application.service.AuctionLifecycleService;
import com.bidding.core.domain.model.Auction;
import com.bidding.core.domain.model.AuctionStatus;
import com.bidding.core.domain.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpirationWorker {

    private final ScheduledExecutorService auctionScheduler;
    private final AuctionLifecycleService auctionLifecycleService;
    private final AuctionRepository auctionRepository;

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * DİNAMİK ASENKRON ZAMANLAMA (ScheduledThreadPool):
     * Açık artırma başlatıldığında bitiş anı hesaplanır ve arka plandaki thread havuzuna teslim edilir.
     * Bu işlem HTTP isteğini atan kullanıcıyı ASLA bekletmez (Non-blocking).
     */
    public void scheduleAuctionExpiration(UUID auctionId, Instant endTime) {
        long delayMillis = Duration.between(Instant.now(), endTime).toMillis();

        if (delayMillis <= 0) {
            log.info("Auction {} has already expired. Triggering expiration immediately on thread [{}]",
                    auctionId, Thread.currentThread().getName());
            executeExpiration(auctionId);
            return;
        }

        log.info("Scheduling auction {} expiration in {} ms on ScheduledThreadPool. Scheduled by thread [{}]",
                auctionId, delayMillis, Thread.currentThread().getName());

        ScheduledFuture<?> future = auctionScheduler.schedule(
                () -> executeExpiration(auctionId),
                delayMillis,
                TimeUnit.MILLISECONDS
        );

        scheduledTasks.put(auctionId, future);
    }

    private void executeExpiration(UUID auctionId) {
        try {
            // Spring Proxy üzerinden çağrı -> @Transactional aktif olur!
            auctionLifecycleService.expireAuction(auctionId);
        } finally {
            scheduledTasks.remove(auctionId);
        }
    }

    /**
     * GÜVENLİK AĞI (Reconciliation Job - Arka Plan Periyodik Denetçisi):
     * Her 30 saniyede bir veritabanını tarar ve kaçırılmış olabilecek
     * süresi geçmiş aktif açık artırmaları yakalayıp kapatır.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    @Transactional
    public void reconcileExpiredAuctions() {
        Instant now = Instant.now();
        List<Auction> expiredAuctions = auctionRepository.findExpiredActiveAuctions(AuctionStatus.ACTIVE, now);

        if (!expiredAuctions.isEmpty()) {
            log.warn("Reconciliation worker detected {} unclosed expired auctions. Closing them now...", expiredAuctions.size());
            for (Auction auction : expiredAuctions) {
                auction.end(now);
                log.info("Reconciled auction {} -> ENDED", auction.getId());
            }
        }
    }
}
