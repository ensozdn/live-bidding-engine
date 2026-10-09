package com.bidding.core.web.controller;

import com.bidding.core.application.dto.AuctionResponse;
import com.bidding.core.application.dto.BidResponse;
import com.bidding.core.application.dto.CreateAuctionRequest;
import com.bidding.core.application.dto.PlaceBidRequest;
import com.bidding.core.application.service.BiddingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class BiddingController {

    private final BiddingService biddingService;

    @PostMapping
    public ResponseEntity<AuctionResponse> createAuction(@Valid @RequestBody CreateAuctionRequest request) {
        AuctionResponse response = biddingService.createAuction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<AuctionResponse> startAuction(@PathVariable("id") UUID id) {
        AuctionResponse response = biddingService.startAuction(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/bids")
    public ResponseEntity<BidResponse> placeBid(
            @PathVariable("id") UUID id,
            @Valid @RequestBody PlaceBidRequest request) {
        BidResponse response = biddingService.placeBid(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> getAuction(@PathVariable("id") UUID id) {
        AuctionResponse response = biddingService.getAuctionWithBids(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AuctionResponse>> getAllAuctions() {
        return ResponseEntity.ok(biddingService.getAllAuctions());
    }
}
