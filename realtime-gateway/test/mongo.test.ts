import { connectMongoDB, disconnectMongoDB } from '../src/database/mongo.connection.js';
import { BidTelemetryModel } from '../src/database/schemas/bid-telemetry.schema.js';
import crypto from 'crypto';

async function runTest() {
  console.log('🧪 MongoDB & Mongoose Pre-Save Hook Testi Başlatılıyor...');
  await connectMongoDB();

  const testEventId = crypto.randomUUID();
  const eventTime = new Date(Date.now() - 150); // 150 ms önce üretilmiş simülasyonu

  const telemetryDoc = new BidTelemetryModel({
    eventId: testEventId,
    auctionId: crypto.randomUUID(),
    bidId: crypto.randomUUID(),
    bidderId: crypto.randomUUID(),
    amount: 35000.0, // 20000 üstü -> HIGH_VALUE_TRANSACTION tetiklemeli
    version: 1,
    eventTimestamp: eventTime
  });

  // .save() çağrıldığında pre-save hook otomatik çalışır!
  const saved = await telemetryDoc.save();

  console.log('✅ Telemetri Kaydı Başarıyla Kaydedildi:');
  console.log({
    eventId: saved.eventId,
    amount: saved.amount,
    latencyMs: `${saved.latencyMs} ms (Hesaplanan Uçtan Uca Gecikme)`,
    auditTag: `${saved.auditTag} (Pre-Save Hook Tarafından Etiketlendi)`,
    processedAt: saved.processedAt
  });

  // Doğrulama Kontrolleri
  if (saved.latencyMs < 100) {
    throw new Error(`Beklenen gecikme >= 100ms olmalıydı, bulunan: ${saved.latencyMs}`);
  }
  if (saved.auditTag !== 'HIGH_VALUE_TRANSACTION') {
    throw new Error(`Beklenen auditTag HIGH_VALUE_TRANSACTION olmalıydı, bulunan: ${saved.auditTag}`);
  }

  // Temizlik
  await BidTelemetryModel.deleteOne({ eventId: testEventId });
  console.log('🧹 Test kaydı başarıyla temizlendi.');

  await disconnectMongoDB();
  console.log('🎉 Pre-Save Hook ve Mongoose Validasyon Testi Başarıyla Tamamlandı!');
}

runTest().catch((err) => {
  console.error('❌ Test Başarısız:', err);
  process.exit(1);
});
