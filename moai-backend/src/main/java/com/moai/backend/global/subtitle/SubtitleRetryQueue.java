package com.moai.backend.global.subtitle;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 자막 스크래핑이 RATE_LIMITED 등으로 실패했을 때 일정 시간 뒤에 재시도하기 위한 큐.
 *
 * 구현은 단순한 ScheduledExecutorService 기반이며 영속화하지 않는다.
 * 애플리케이션이 재시작되면 대기 중인 재시도는 손실되지만, 자막은 best-effort 데이터이므로 허용한다.
 */
@Slf4j
@Component
public class SubtitleRetryQueue {

    private final ScheduledExecutorService scheduler;

    public SubtitleRetryQueue() {
        AtomicInteger counter = new AtomicInteger(1);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "subtitle-retry-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 재시도 작업을 일정 시간 후 실행한다.
     *
     * @param retryTask 재시도 시 수행할 작업
     * @param delaySec  지연 시간(초)
     */
    public void enqueue(Runnable retryTask, long delaySec) {
        try {
            scheduler.schedule(() -> {
                try {
                    retryTask.run();
                } catch (Exception e) {
                    log.warn("자막 재시도 작업 중 예외 발생: {}", e.getMessage(), e);
                }
            }, delaySec, TimeUnit.SECONDS);
            log.info("자막 스크래핑 재시도 예약 — {}초 뒤 실행", delaySec);
        } catch (Exception e) {
            log.warn("자막 재시도 예약 실패: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
