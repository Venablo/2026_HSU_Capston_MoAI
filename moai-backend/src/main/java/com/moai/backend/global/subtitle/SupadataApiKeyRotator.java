package com.moai.backend.global.subtitle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Supadata API 키 풀 관리 + 회전.
 *
 * - 다중 키를 콤마 구분 문자열(`supadata.api-keys`)에서 받아 보관 (공백 제거 + 빈 값 필터)
 * - currentIdx 포인터 기반으로 한 시점에 1개 키 사용
 * - 429(quota/rate) 시 호출부가 {@link #rotateIfCurrent(String)} 로 다음 키 진행 요청
 * - 모든 키 소진(포인터가 size 이상) 시 호출부에서 RATE_LIMITED 그대로 던지면 됨
 * - 재시작 시 currentIdx=0 으로 복귀 (메모리 보관, Redis 미사용)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "subtitle.provider", havingValue = "supadata")
public class SupadataApiKeyRotator {

    private final List<String> keys;
    private final AtomicInteger currentIdx = new AtomicInteger(0);

    public SupadataApiKeyRotator(@Value("${supadata.api-keys:}") List<String> rawKeys) {
        this.keys = rawKeys.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (this.keys.isEmpty()) {
            log.warn("Supadata 키가 비어있음 — SUPADATA_API_KEYS 또는 SUPADATA_API_KEY 설정 필요");
        } else {
            log.info("Supadata 키 풀 로드 — {}개", this.keys.size());
        }
    }

    public int size() {
        return keys.size();
    }

    /** 현재 포인터의 키. 모두 소진됐으면 빈 문자열. */
    public String current() {
        int idx = currentIdx.get();
        if (idx >= keys.size()) return "";
        return keys.get(idx);
    }

    /**
     * 인자로 받은 키가 현재 포인터의 키와 동일하면 포인터를 1 진행한다.
     * 다른 스레드가 이미 회전시킨 경우에는 중복 진행하지 않는다.
     *
     * @return 회전 후에도 시도 가능한 키가 남아있으면 true
     */
    public synchronized boolean rotateIfCurrent(String exhaustedKey) {
        int idx = currentIdx.get();
        if (idx < keys.size() && keys.get(idx).equals(exhaustedKey)) {
            currentIdx.incrementAndGet();
        }
        return currentIdx.get() < keys.size();
    }
}
