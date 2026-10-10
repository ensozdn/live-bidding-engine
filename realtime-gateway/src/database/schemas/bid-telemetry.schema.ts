import { Schema, model, Document } from 'mongoose';

export interface IBidTelemetry extends Document {
  eventId: string;
  auctionId: string;
  bidId: string;
  bidderId: string;
  amount: number;
  version: number;
  eventTimestamp: Date;
  processedAt: Date;
  latencyMs: number;
  auditTag: string;
}

const BidTelemetrySchema = new Schema<IBidTelemetry>(
  {
    eventId: { type: String, required: true, unique: true },
    auctionId: { type: String, required: true, index: true },
    bidId: { type: String, required: true },
    bidderId: { type: String, required: true },
    amount: { type: Number, required: true, min: [0.01, 'Amount must be positive'] },
    version: { type: Number, required: true },
    eventTimestamp: { type: Date, required: true },
    processedAt: { type: Date, default: () => new Date() },
    latencyMs: { type: Number, default: 0 },
    auditTag: { type: String, default: 'NORMAL' }
  },
  {
    timestamps: true,
    collection: 'bid_telemetry'
  }
);

// Bitişik İndeks: Açık artırma bazlı zaman serisi sorguları için
BidTelemetrySchema.index({ auctionId: 1, eventTimestamp: -1 });

/**
 * MONGOOSE PRE-SAVE HOOK (Veri Zenginleştirme & Telemetri Analizi):
 * Neden Pre-Save Hook?
 * - Controller veya Consumer kodunu kirletmeden veri tabanına yazılmadan hemen önce
 *   otomatik telemetri metrikleri hesaplanır (Single Responsibility).
 * - Spring Boot'un event'i ürettiği an ile Node.js'in kaydettiği an arasındaki
 *   uçtan uca ağ ve kuyruk gecikmesi (Latency) milisaniye olarak ölçülür.
 * - Yüksek tutarlı teklifler audit için otomatik etiketlenir.
 */
BidTelemetrySchema.pre('save', function (next) {
  const now = new Date();
  if (!this.processedAt) {
    this.processedAt = now;
  }

  // Gecikme Telemetrisi (End-to-End Event Latency)
  const latency = this.processedAt.getTime() - new Date(this.eventTimestamp).getTime();
  this.latencyMs = Math.max(0, latency);

  // Otomatik Audit Etiketleme (İş Kuralı)
  if (this.amount >= 20000) {
    this.auditTag = 'HIGH_VALUE_TRANSACTION';
  } else {
    this.auditTag = 'STANDARD_BID';
  }

  next();
});

export const BidTelemetryModel = model<IBidTelemetry>('BidTelemetry', BidTelemetrySchema);
