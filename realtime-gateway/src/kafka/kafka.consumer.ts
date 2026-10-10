import { Kafka, Consumer } from 'kafkajs';
import { ENV } from '../config/env.config.js';
import { BidTelemetryModel } from '../database/schemas/bid-telemetry.schema.js';
import { BiddingGateway } from '../websocket/bidding.gateway.js';

/**
 * KAFKA CONSUMER (Single Responsibility: Event Tüketimi, Telemetri Kaydı & WebSocket Tetikleme)
 * - Prensip: Dependency Inversion (BiddingGateway dışarıdan enjekte edilir)
 * - Kafka'dan akan ham mesajı okur, MongoDB'ye telemetri olarak yazar ve WebSocket'e aktarır.
 */
export class AuctionKafkaConsumer {
  private consumer: Consumer;

  constructor(private readonly gateway: BiddingGateway) {
    const kafka = new Kafka({
      clientId: 'realtime-gateway-worker',
      brokers: [ENV.KAFKA_BROKER]
    });

    this.consumer = kafka.consumer({ groupId: ENV.KAFKA_GROUP_ID });
  }

  public async start(): Promise<void> {
    await this.consumer.connect();
    console.log(`📥 Kafka Consumer bağlandı: Broker [${ENV.KAFKA_BROKER}], Grup [${ENV.KAFKA_GROUP_ID}]`);

    await this.consumer.subscribe({
      topic: ENV.KAFKA_TOPIC,
      fromBeginning: false
    });

    await this.consumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        if (!message.value) return;

        try {
          const rawPayload = message.value.toString();
          const event = JSON.parse(rawPayload);
          const key = message.key?.toString();

          console.log(`\n📨 Kafka Mesajı Alındı -> Topic: [${topic}], Partition: [${partition}], Key: [${key}], EventType: [${event.eventType}]`);

          if (event.eventType === 'BID_PLACED') {
            // 1. Mongoose ile MongoDB'ye Telemetri Kaydı (Pre-save hook otomatik çalışır)
            const telemetry = await BidTelemetryModel.create({
              eventId: event.eventId,
              auctionId: event.auctionId,
              bidId: event.bidId,
              bidderId: event.bidderId,
              amount: event.amount,
              version: event.version,
              eventTimestamp: new Date(event.timestamp)
            });

            console.log(`🍃 Mongoose Telemetri Kaydedildi: Latency: [${telemetry.latencyMs}ms], Audit: [${telemetry.auditTag}]`);

            // 2. WebSocket ile ilgili odaya anlık fırlatma
            this.gateway.broadcastBid(event.auctionId, event);
          } else if (event.eventType === 'AUCTION_ENDED') {
            console.log(`🏁 Açık Artırma Sona Erdi: [${event.auctionId}], Kazanan Fiyat: [${event.finalPrice}]`);
            this.gateway.broadcastAuctionEnded(event.auctionId, event);
          }
        } catch (error) {
          console.error('❌ Kafka mesaj işleme hatası:', error);
        }
      }
    });
  }

  public async stop(): Promise<void> {
    await this.consumer.disconnect();
    console.log('📥 Kafka Consumer bağlantısı kapatıldı');
  }
}
