import { Server as HttpServer } from 'http';
import { Server, Socket } from 'socket.io';

/**
 * WEBSOCKET GATEWAY (Single Responsibility: İstemci Bağlantıları ve Oda Yönetimi)
 * - Prensip: Less Code & High Cohesion
 * - Her açık artırma bir "Room" (Oda) olarak yönetilir.
 * - İstemci sadece ilgilendiği açık artırmaya subscribe olur (Odaya katılır).
 * - Fiyat güncellemesi geldiğinde sadece o odadaki kullanıcılara tek TCP hattından fırlatılır.
 */
export class BiddingGateway {
  private io: Server;

  constructor(server: HttpServer) {
    this.io = new Server(server, {
      cors: { origin: '*' }
    });
    this.registerSocketEvents();
  }

  private registerSocketEvents(): void {
    this.io.on('connection', (socket: Socket) => {
      console.log(`🔌 Yeni WebSocket İstemcisi Bağlandı: [${socket.id}]`);

      // İstemci belirli bir açık artırma odasına abone olur
      socket.on('join:auction', (auctionId: string) => {
        socket.join(auctionId);
        console.log(`🏷️  İstemci [${socket.id}] açık artırma odasına katıldı: [${auctionId}]`);
      });

      // Odadan ayrılma
      socket.on('leave:auction', (auctionId: string) => {
        socket.leave(auctionId);
        console.log(`👋 İstemci [${socket.id}] odadan ayrıldı: [${auctionId}]`);
      });

      socket.on('disconnect', () => {
        console.log(`❌ İstemci Ayrıldı: [${socket.id}]`);
      });
    });
  }

  /**
   * Sadece ilgili açık artırma odasındaki istemcilere anlık teklif fırlatır
   */
  public broadcastBid(auctionId: string, payload: unknown): void {
    this.io.to(auctionId).emit('bid:placed', payload);
    console.log(`📡 WebSocket Broadcast [bid:placed] -> Oda: [${auctionId}]`);
  }

  /**
   * Açık artırma sonlandığında odaya haber verir
   */
  public broadcastAuctionEnded(auctionId: string, payload: unknown): void {
    this.io.to(auctionId).emit('auction:ended', payload);
    console.log(`🏁 WebSocket Broadcast [auction:ended] -> Oda: [${auctionId}]`);
  }

  public async close(): Promise<void> {
    await this.io.close();
  }
}
