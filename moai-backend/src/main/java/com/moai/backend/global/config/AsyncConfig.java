package com.moai.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 커리큘럼 비동기 enrichment 전용 스레드 풀.
     * 각 주차별 파이프라인(LLM 영상 추천 → 자막 스크래핑 → 키워드 추출)이 이 풀에서 병렬 실행된다.
     * IO-bound 작업이므로 core=4, max=8로 설정하여 LLM API 과부하를 방지한다.
     */
    @Bean(name = "curriculumTaskExecutor")
    public TaskExecutor curriculumTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("curriculum-");
        executor.initialize();
        return executor;
    }
}
