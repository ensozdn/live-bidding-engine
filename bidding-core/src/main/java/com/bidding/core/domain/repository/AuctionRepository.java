package com.bidding.core.domain.repository;

import com.bidding.core.domain.model.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, UUID> {
}
