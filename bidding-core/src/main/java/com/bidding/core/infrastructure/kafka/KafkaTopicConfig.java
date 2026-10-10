package com.bidding.core.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String AUCTION_EVENTS_TOPIC = "auction-events";

    /**
     * KAFKA TOPIC MİMARİSİ:
     * - 3 Partition: Yatay ölçekleme (Parallel Consumer) sağlar.
     * - Replication Factor 1: Tek broker'lı yerel geliştirme için.
     *
     * ÖNEMLİ KURAL:
     * Aynı açık artırmaya (auctionId) ait tüm event'ler AYNI partition'a gitmek zorundadır.
     * Bu sayede Kafka bize partition düzeyinde Message Ordering (kronolojik sıra) garantisi verir.
     */
    @Bean
    public NewTopic auctionEventsTopic() {
        return TopicBuilder.name(AUCTION_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
