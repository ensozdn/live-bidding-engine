package com.bidding.core.infrastructure.kafka;

import com.bidding.core.domain.event.AuctionEndedEvent;
import com.bidding.core.domain.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * KAFKA PARTITION KEY & ORDERING MİMARİSİ:
     * - Topic: auction-events
     * - Key: auctionId.toString()
     *
     * NEDEN KEY KULLANIYORUZ?
     * Kafka'da küresel bir sıralama (Global Order) yoktur. Kafka sadece aynı Partition
     * içerisindeki mesajların sırasını (Partition-level Order) garanti eder.
     * Mesaj Key olarak 'auctionId' verdiğimizde, DefaultPartitioner hash(key) % partitionCount formülünü
     * uygular ve o açık artırmaya ait TÜM teklifleri İSTİSNASIZ AYNI PARTITION'A yazar.
     * Bu sayede tüketici (Node.js/NestJS) 100₺ teklifini 105₺'den önce okumayı matematiksel olarak garantiler!
     *
     * ASENKRON HABERLEŞME:
     * future.whenComplete() ile callback dinlenir. HTTP thread'i ASLA bloke edilmez (future.join() YOKTUR).
     */
    public void publishBidPlacedEvent(BidPlacedEvent event) {
        String key = event.auctionId().toString();

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaTopicConfig.AUCTION_EVENTS_TOPIC,
                key,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("KAFKA HATA: BidPlacedEvent gönderilemedi! Auction: {}, Hata: {}",
                        event.auctionId(), ex.getMessage(), ex);
            } else {
                log.info("KAFKA SUCCESS -> BidPlacedEvent iletildi. Topic: [{}], Key (AuctionId): [{}], Partition: [{}], Offset: [{}]",
                        result.getRecordMetadata().topic(),
                        key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void publishAuctionEndedEvent(AuctionEndedEvent event) {
        String key = event.auctionId().toString();

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaTopicConfig.AUCTION_EVENTS_TOPIC,
                key,
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("KAFKA HATA: AuctionEndedEvent gönderilemedi! Auction: {}, Hata: {}",
                        event.auctionId(), ex.getMessage(), ex);
            } else {
                log.info("KAFKA SUCCESS -> AuctionEndedEvent iletildi. Topic: [{}], Key (AuctionId): [{}], Partition: [{}], Offset: [{}]",
                        result.getRecordMetadata().topic(),
                        key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
