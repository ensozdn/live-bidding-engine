package com.bidding.core.application.dto;

import com.bidding.core.domain.model.Auction;
import com.bidding.core.domain.model.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuctionResponse(
        UUID id,
        String title,
        String itemDescription,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal minIncrement,
        UUID highestBidderId,
        AuctionStatus status,
        Instant startTime,
        Instant endTime,
        Long version,
        List<BidResponse> bids
) {
    public static AuctionResponse from(Auction auction) {
        List<BidResponse> bidResponses = auction.getBids() != null
                ? auction.getBids().stream().map(BidResponse::from).toList()
                : List.of();

        return new AuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getItemDescription(),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                auction.getMinIncrement(),
                auction.getHighestBidderId(),
                auction.getStatus(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getVersion(),
                bidResponses
        );
    }
}
