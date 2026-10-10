import dotenv from 'dotenv';
dotenv.config();

export const ENV = {
  PORT: Number(process.env.PORT) || 4000,
  MONGO_URI: process.env.MONGO_URI || 'mongodb://admin:adminpassword@localhost:27017/bidding_telemetry?authSource=admin',
  KAFKA_BROKER: process.env.KAFKA_BROKER || 'localhost:9092',
  KAFKA_TOPIC: process.env.KAFKA_TOPIC || 'auction-events',
  KAFKA_GROUP_ID: process.env.KAFKA_GROUP_ID || 'realtime-gateway-group'
} as const;
