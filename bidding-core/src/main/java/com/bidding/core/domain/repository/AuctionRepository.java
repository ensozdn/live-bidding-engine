package com.bidding.core.domain.repository;

import com.bidding.core.domain.model.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    /**
     * N+1 PROBLEMİ ÇÖZÜMÜ:
     * bids koleksiyonu FetchType.LAZY olduğu için standart findById() çağrıldığında
     * ilişkili teklifler yüklenmez. Eğer sonrasında auction.getBids() okunursa
     * Hibernate her bir auction için veritabanına ek SELECT atar (N+1 felaketi).
     *
     * LEFT JOIN FETCH kullanarak Auction ve ona bağlı Bid kayıtlarını
     * TEK BİR SQL sorgusunda çekeriz.
     */
    @Query("SELECT DISTINCT a FROM Auction a LEFT JOIN FETCH a.bids WHERE a.id = :id")
    Optional<Auction> findByIdWithBids(@Param("id") UUID id);

    /**
     * Arka plan güvenlik ağı (Reconciliation Job) için:
     * Süresi dolmuş ancak sunucu çökmesi/yeniden başlaması nedeniyle ENDED yapılmamış
     * ACTIVE açık artırmaları bulur.
     */
    @Query("SELECT a FROM Auction a WHERE a.status = :status AND a.endTime <= :now")
    java.util.List<Auction> findExpiredActiveAuctions(
            @Param("status") com.bidding.core.domain.model.AuctionStatus status,
            @Param("now") java.time.Instant now
    );
}
