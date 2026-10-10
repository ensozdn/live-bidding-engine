package com.bidding.core.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class ThreadPoolConfig {

    /**
     * MULTITHREADING MİMARİSİ:
     * Neden ScheduledExecutorService?
     * 1. CPU'yu Thread.sleep() gibi yöntemlerle bloke etmeyiz.
     * 2. Zamanlanan görevler non-blocking olarak işletim sistemi zamanlayıcısına (epoll/kqueue) benzer bir
     *    zaman çarkı (DelayQueue) mantığıyla hafızada tutulur; zamanı gelen iş thread havuzundaki müsait işçiye atanır.
     * 3. Özel ThreadFactory ile her işçiye anlamlı isimler veririz (APM / jstack analizlerinde hayati önem taşır).
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService auctionScheduler() {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        log.info("Initializing Auction ScheduledThreadPool with pool size: {}", poolSize);

        ThreadFactory namedThreadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "auction-scheduler-worker-" + threadNumber.getAndIncrement());
                t.setDaemon(false); // Görevi tamamlamadan uygulamanın aniden kapanmasını önler
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };

        return new ScheduledThreadPoolExecutor(poolSize, namedThreadFactory) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                if (t != null) {
                    log.error("Unhandled exception caught in auction scheduler thread execution!", t);
                }
            }
        };
    }
}
