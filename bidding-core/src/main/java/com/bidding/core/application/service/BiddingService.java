package com.bidding.core.application.service;

import com.bidding.core.application.dto.AuctionResponse;
import com.bidding.core.application.dto.BidResponse;
import com.bidding.core.application.dto.CreateAuctionRequest;
import com.bidding.core.application.dto.PlaceBidRequest;
import com.bidding.core.domain.model.Auction;
import com.bidding.core.domain.model.Bid;
import com.bidding.core.domain.repository.AuctionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiddingService {

    private final AuctionRepository auctionRepository;
    private final com.bidding.core.application.worker.AuctionExpirationWorker auctionExpirationWorker;

    /**
     * Yeni açık artırma oluşturur.
     */
    @Transactional
    public AuctionResponse createAuction(CreateAuctionRequest request) {
        log.info("Creating new auction: {}", request.title());
        Auction auction = Auction.create(
                request.title(),
                request.itemDescription(),
                request.startingPrice(),
                request.minIncrement(),
                request.startTime(),
                request.endTime()
        );
        Auction saved = auctionRepository.save(auction);
        return AuctionResponse.from(saved);
    }

    /**
     * Açık artırmayı başlatır ve arka plan işçisini (ScheduledThreadPool) tetikler.
     */
    @Transactional
    public AuctionResponse startAuction(UUID auctionId) {
        log.info("Starting auction: {}", auctionId);
        Auction auction = getAuctionOrThrow(auctionId);
        auction.start(Instant.now());

        // MULTITHREADING: Bitiş süresi için arka plan işçisine zamanlama kaydı girilir
        auctionExpirationWorker.scheduleAuctionExpiration(auction.getId(), auction.getEndTime());

        return AuctionResponse.from(auction);
    }

    /**
     * HİBERNATE DIRTY CHECKING & OPTIMISTIC LOCKING VİTRİNİ:
     * 1. findById ile Auction'ı çekeriz -> Entity 'MANAGED' state'e geçer.
     * 2. auction.placeBid() çağrılır -> Invariant doğrulanır ve bellek içi nesne güncellenir.
     * 3. 'auctionRepository.save(auction)' ÇAĞIRMIYORUZ!
     * 4. Transaction sonlanırken (@Transactional commit anı):
     *    - Dirty checking snapshot ile mevcut durumu kıyaslar.
     *    - 'UPDATE auctions SET ..., version = version + 1 WHERE id = ? AND version = ?' çalıştırılır.
     *    - Yeni teklif için 'INSERT INTO bids ...' çalıştırılır.
     *    - Eğer versiyon uyuşmazsa OptimisticLockException fırlatılır.
     */
    @Transactional
    public BidResponse placeBid(UUID auctionId, PlaceBidRequest request) {
        log.info("Processing bid for auction: {}, bidder: {}, amount: {}", auctionId, request.bidderId(), request.amount());

        Auction auction = getAuctionOrThrow(auctionId);

        // Aggregate Root üzerinden invariant korumalı teklif verme
        Bid newBid = auction.placeBid(request.bidderId(), request.amount(), Instant.now());

        // Dirty checking: Transaction commit anında Hibernate hem auctions tablosunu
        // güncelleyecek hem de CascadeType.ALL sayesinde bids tablosuna insert atacak!
        return BidResponse.from(newBid);
    }

    /**
     * N+1 Problemini önlemek için JOIN FETCH sorgusu kullanır.
     */
    @Transactional(readOnly = true)
    public AuctionResponse getAuctionWithBids(UUID auctionId) {
        return auctionRepository.findByIdWithBids(auctionId)
                .map(AuctionResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Auction not found: " + auctionId));
    }

    @Transactional(readOnly = true)
    public List<AuctionResponse> getAllAuctions() {
        return auctionRepository.findAll().stream()
                .map(AuctionResponse::from)
                .toList();
    }

    private Auction getAuctionOrThrow(UUID auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new EntityNotFoundException("Auction not found: " + auctionId));
    }
}
