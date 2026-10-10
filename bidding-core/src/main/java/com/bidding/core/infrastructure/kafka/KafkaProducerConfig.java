package com.bidding.core.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    /**
     * KAFKA SERIALIZATION & CROSS-LANGUAGE UYUMLULUK:
     * Node.js / NestJS tüketicisi (consumer) ile konuşurken:
     * 1. Java 8+ 'Instant' tipini serialize edebilmek için JavaTimeModule ekliyoruz.
     * 2. 'setAddTypeInfo(false)' yapıyoruz: Spring varsayılan olarak header'lara
     *    '__TypeId__: com.bidding.core.domain.event.BidPlacedEvent' gibi Java sınıf isimleri basar.
     *    Farklı dildeki (Node.js/Go/Python) tüketiciler bu Java header'larını bilmez ve JSON parse ederken
     *    header kirliliği yaşanır. Temiz, ham JSON göndermek mikroservisler arası standarttır.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        JsonSerializer<Object> jsonSerializer = new JsonSerializer<>(objectMapper);
        jsonSerializer.setAddTypeInfo(false);

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(configProps, new StringSerializer(), jsonSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
