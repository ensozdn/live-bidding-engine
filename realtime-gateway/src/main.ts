import http from 'http';
import { ENV } from './config/env.config.js';
import { connectMongoDB, disconnectMongoDB } from './database/mongo.connection.js';
import { BiddingGateway } from './websocket/bidding.gateway.js';
import { AuctionKafkaConsumer } from './kafka/kafka.consumer.js';

/**
 * COMPOSITION ROOT (Main Entry Point)
 * - Prensip: Clean Architecture & Dependency Injection
 * - Tüm modülleri tek bir yerde birleştirir, uygulama yaşam döngüsünü yönetir.
 */
async function bootstrap(): Promise<void> {
  console.log('🚀 Real-Time Live Bidding Gateway Başlatılıyor...');

  // 1. Veritabanı Katmanı
  await connectMongoDB();

  // 2. HTTP ve WebSocket Sunucusu
  const httpServer = http.createServer((req, res) => {
    if (req.url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'UP', service: 'realtime-gateway' }));
      return;
    }
    res.writeHead(404);
    res.end();
  });

  const gateway = new BiddingGateway(httpServer);

  // 3. Kafka Consumer
  const kafkaConsumer = new AuctionKafkaConsumer(gateway);
  await kafkaConsumer.start();

  // 4. Sunucuyu Dinlemeye Başla
  httpServer.listen(ENV.PORT, () => {
    console.log(`⚡ Real-Time Gateway hazır: http://localhost:${ENV.PORT} (WebSocket Port: ${ENV.PORT})\n`);
  });

  // Graceful Shutdown (Temiz Kapanma)
  const shutdown = async () => {
    console.log('\n🛑 Gateway kapatılıyor...');
    await kafkaConsumer.stop();
    await gateway.close();
    await disconnectMongoDB();
    httpServer.close(() => {
      console.log('🏁 Sunucu kapatıldı.');
      process.exit(0);
    });
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

bootstrap().catch((err) => {
  console.error('❌ Başlatma hatası:', err);
  process.exit(1);
});
