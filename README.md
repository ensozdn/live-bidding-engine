# Real-Time Live Bidding Engine ⚡️🔨

Mikroservis mimarisi ve event-driven omurga üzerinde inşa edilen yüksek trafikli Canlı Açık Artırma Platformu (Live Bidding Engine).

## Mimari Bakış ve 7 Temel Yapı Taşı

1. **Domain-Driven Design (DDD)**: Spring Boot tarafında Aggregate Root, Invariant koruması ve Rich Domain Model.
2. **Hibernate (JPA)**: PostgreSQL üzerinde Transaction yönetimi, Dirty Checking mekanizması, N+1 problemi analizi ve `@Version` (Optimistic Locking).
3. **Multithreading**: Java tarafında `ScheduledThreadPool` ile açık artırma sürelerini takip eden, CPU'yu bloke etmeyen arka plan işçileri.
4. **Apache Kafka (KRaft)**: Servisler arası asenkron event omurgası, Topic, Partition ve Auction-ID bazlı Message Ordering garantisi.
5. **Asenkron Haberleşme**: Spring Boot'un kullanıcıyı bekletmeden (decoupled) Kafka'ya event fırlatması ve Node.js worker'ın bunu tüketmesi.
6. **Mongoose & MongoDB**: Node.js worker'ın Kafka'dan akan ham teklif telemetrisini ve loglarını şema validasyonu ve pre-save hook'ları ile kaydetmesi.
7. **WebSocket**: NestJS/Node.js üzerinden istemcilere tek bir TCP hattından anlık fiyat güncellemelerinin fırlatılması.

---

## Proje Yapısı

```text
live-bidding-engine/
├── infra/                 # Docker Compose (PostgreSQL, Kafka KRaft, MongoDB)
├── bidding-core/          # Spring Boot (DDD, JPA, Lock, ThreadPool, Kafka Producer)
└── realtime-gateway/      # NestJS / Node.js (Kafka Consumer, Mongoose, WebSocket Gateway)
```

---

## Başlangıç (Altyapı)

Altyapı servislerini başlatmak için:

```bash
cd infra
docker compose up -d
```
