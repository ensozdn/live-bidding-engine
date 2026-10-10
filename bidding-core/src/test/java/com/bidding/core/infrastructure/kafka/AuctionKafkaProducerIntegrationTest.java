package com.bidding.core.infrastructure.kafka;

import com.bidding.core.application.dto.AuctionResponse;
import com.bidding.core.application.dto.CreateAuctionRequest;
import com.bidding.core.application.dto.PlaceBidRequest;
import com.bidding.core.application.service.BiddingService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Apache Kafka Producer & Partition Ordering Integration Tests")
class AuctionKafkaProducerIntegrationTest {

    @Autowired
    private BiddingService biddingService;

    @Test
    @DisplayName("Kafka Entegrasyonu: Teklif verildiğinde event auctionId key'i ile aynı partition'a yazılmalı")
    void shouldPublishBidPlacedEventWithAuctionIdAsPartitionKey() {
        // 1. Canlı Kafka'yı dinlemek için geçici bir Test Consumer oluştur
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verifier-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(KafkaTopicConfig.AUCTION_EVENTS_TOPIC));

            // 2. Açık artırma yarat ve başlat
            Instant now = Instant.now();
            CreateAuctionRequest request = new CreateAuctionRequest(
                    "Patek Philippe Nautilus 5711",
                    "Blue dial luxury timepiece",
                    new BigDecimal("50000.00"),
                    new BigDecimal("1000.00"),
                    now.minus(1, ChronoUnit.HOURS),
                    now.plus(2, ChronoUnit.HOURS)
            );

            AuctionResponse auction = biddingService.createAuction(request);
            UUID auctionId = auction.id();
            biddingService.startAuction(auctionId);

            // 3. İki ardışık teklif ver
            UUID bidderAlice = UUID.randomUUID();
            UUID bidderBob = UUID.randomUUID();

            biddingService.placeBid(auctionId, new PlaceBidRequest(bidderAlice, new BigDecimal("50000.00")));
            biddingService.placeBid(auctionId, new PlaceBidRequest(bidderBob, new BigDecimal("52000.00")));

            // 4. Kafka'dan event'leri çek (Poll)
            List<ConsumerRecord<String, String>> receivedRecords = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 10000; // 10 saniye zaman aşımı

            while (System.currentTimeMillis() < deadline && receivedRecords.size() < 2) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (auctionId.toString().equals(record.key())) {
                        receivedRecords.add(record);
                    }
                }
            }

            // 5. DOĞRULAMALAR (Ordering & Key Garantisi)
            assertEquals(2, receivedRecords.size(), "Kafka topic'inden 2 adet event başarıyla okunmalıydı!");

            ConsumerRecord<String, String> firstRecord = receivedRecords.get(0);
            ConsumerRecord<String, String> secondRecord = receivedRecords.get(1);

            // Kural 1: Mesaj Key'i kesinlikle auctionId olmalıdır
            assertEquals(auctionId.toString(), firstRecord.key());
            assertEquals(auctionId.toString(), secondRecord.key());

            // Kural 2: EN KRİTİK KURAL -> Her iki mesaj da AYNI partition'a düşmelidir!
            assertEquals(firstRecord.partition(), secondRecord.partition(),
                    "Aynı açık artırmaya ait tüm teklifler sıralama garantisi (ordering) için AYNI partition'a gitmeli!");

            // Kural 3: İkinci mesajın offset'i birinci mesajdan büyük olmalıdır (Kronolojik sıra)
            assertTrue(secondRecord.offset() > firstRecord.offset(),
                    "İkinci teklifin offset değeri birinciden büyük olmalıdır!");

            // Kural 4: JSON payload doğrulaması
            assertTrue(firstRecord.value().contains("50000.00"));
            assertTrue(secondRecord.value().contains("52000.00"));
            assertTrue(firstRecord.value().contains("BID_PLACED"));

            System.out.printf("KAFKA TESTİ BAŞARILI! Partition: %d, 1. Offset: %d, 2. Offset: %d%n",
                    firstRecord.partition(), firstRecord.offset(), secondRecord.offset());
        }
    }
}
