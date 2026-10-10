import mongoose from 'mongoose';
import { ENV } from '../config/env.config.js';

export async function connectMongoDB(): Promise<void> {
  try {
    await mongoose.connect(ENV.MONGO_URI);
    console.log('🍃 MongoDB bağlantısı başarılı: bidding_telemetry');
  } catch (error) {
    console.error('❌ MongoDB bağlantı hatası:', error);
    process.exit(1);
  }
}

export async function disconnectMongoDB(): Promise<void> {
  await mongoose.disconnect();
  console.log('🍃 MongoDB bağlantısı kapatıldı');
}
