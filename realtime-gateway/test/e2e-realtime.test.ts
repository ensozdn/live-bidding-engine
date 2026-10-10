import http from 'http';
import { io as ClientSocket } from 'socket.io-client';
import { Kafka } from 'kafkajs';
import crypto from 'crypto';
import { connectMongoDB, disconnectMongoDB } from '../src/database/mongo.connection.js';
import { BiddingGateway } from '../src/websocket/bidding.gateway.js';
import { AuctionKafkaConsumer } from '../src/kafka/kafka.consumer.js';
import { BidTelemetryModel } from '../src/database/schemas/bid-telemetry.schema.js';

async function runE2ETest() {
  console.log('🧪 Uçtan Uca (E2E) Real-Time Bidding Testi Başlatılıyor...');

  // 1. Altyapı Bileşenlerini Başlat
  await connectMongoDB();

  const testPort = 4001;
  const httpServer = http.createServer();
  const gateway = new BiddingGateway(httpServer);
  const kafkaConsumer = new AuctionKafkaConsumer(gateway);
  await kafkaConsumer.start();

  await new Promise<void>((resolve) => httpServer.listen(testPort, resolve));
  console.log(`📡 Test Gateway Port ${testPort} üzerinde aktif`);

  // 2. Bir İstemci (Tarayıcı Simülasyonu) WebSocket'e Bağlanır
  const targetAuctionId = crypto.randomUUID();
  const clientSocket = ClientSocket(`http://localhost:${testPort}`);

  await new Promise<void>((resolve) => {
    clientSocket.on('connect', () => {
      console.log('👤 [İstemci Simülasyonu] WebSocket bağlantısı kuruldu:', clientSocket.id);
      // İstemci odaya katılır
      clientSocket.emit('join:auction', targetAuctionId);
      setTimeout(resolve, 500); // Odaya katılma onayı için kısa bekleme
    });
  });

  // 3. İstemcinin 'bid:placed' dinleyicisi kurulur
  const receivedEvents: any[] = [];
  clientSocket.on('bid:placed', (data) => {
    console.log('🎉 [İstemci Simülasyonu] Canlı Teklif Alındı (WebSocket):', data);
    receivedEvents.push(data);
  });

  // 4. Spring Boot'un Kafka'ya Event Fırlatmasını Simüle Et
  const testEventId = crypto.randomUUID();
  const kafka = new Kafka({ brokers: ['localhost:9092'] });
  const producer = kafka.producer();
  await producer.connect();

  const mockBidPayload = {
    eventId: testEventId,
    eventType: 'BID_PLACED',
    auctionId: targetAuctionId,
    bidId: crypto.randomUUID(),
    bidderId: crypto.randomUUID(),
    amount: 75000.0,
    version: 3,
    timestamp: new Date().toISOString()
  };

  console.log('🚀 Spring Boot Simülasyonu: Kafka topic auction-events içine event basılıyor...');
  await producer.send({
    topic: 'auction-events',
    messages: [
      {
        key: targetAuctionId,
        value: JSON.stringify(mockBidPayload)
      }
    ]
  });
  await producer.disconnect();

  // 5. Mesajın Kafka'dan okunup WebSocket'e ulaşmasını bekle (Max 5 saniye)
  const deadline = Date.now() + 5000;
  while (Date.now() < deadline && receivedEvents.length === 0) {
    await new Promise((r) => setTimeout(r, 200));
  }

  // 6. Doğrulamalar
  if (receivedEvents.length === 0) {
    throw new Error('❌ HATA: İstemci WebSocket üzerinden teklifi alamadı!');
  }

  const received = receivedEvents[0];
  if (received.amount !== 75000.0 || received.auctionId !== targetAuctionId) {
    throw new Error('❌ HATA: Alınan teklif verisi uyuşmuyor!');
  }

  // 7. MongoDB Telemetri Doğrulaması
  const savedTelemetry = await BidTelemetryModel.findOne({ eventId: testEventId });
  if (!savedTelemetry) {
    throw new Error('❌ HATA: Mongoose telemetri kaydı MongoDB içinde bulunamadı!');
  }

  console.log('\n📊 Mongoose & MongoDB Telemetri Doğrulandı:');
  console.log({
    eventId: savedTelemetry.eventId,
    amount: savedTelemetry.amount,
    latencyMs: `${savedTelemetry.latencyMs} ms`,
    auditTag: savedTelemetry.auditTag // HIGH_VALUE_TRANSACTION olmalı
  });

  if (savedTelemetry.auditTag !== 'HIGH_VALUE_TRANSACTION') {
    throw new Error('❌ HATA: 75000 tutarlı teklif HIGH_VALUE_TRANSACTION olarak etiketlenmeliydi!');
  }

  // 8. Temizlik ve Kapanış
  await BidTelemetryModel.deleteOne({ eventId: testEventId });
  clientSocket.disconnect();
  await kafkaConsumer.stop();
  await gateway.close();
  await disconnectMongoDB();
  await new Promise<void>((resolve) => httpServer.close(() => resolve()));

  console.log('\n🏆 E2E TESTİ %100 BAŞARILI! (Spring/Kafka -> Node.js -> Mongo Telemetry -> WebSocket)');
}

runE2ETest().catch((err) => {
  console.error('❌ E2E Test Başarısız:', err);
  process.exit(1);
});
