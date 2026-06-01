package com.moai.backend.domain.eventlog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatternDetectionService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final Duration EVENT_TTL          = Duration.ofMinutes(10);
    private static final Duration COOLDOWN_TTL       = Duration.ofMinutes(5);
    private static final int      REWIND_THRESHOLD   = 3;
    private static final double   REWIND_RANGE_SEC   = 30.0;
    private static final int      SKIP_THRESHOLD     = 3;
    private static final int      SPEEDUP_THRESHOLD_SEC = 180;

    public record PatternResult(boolean triggered, String patternType) {
        public static PatternResult notTriggered() {
            return new PatternResult(false, null);
        }
    }

    // ──────────────────────────────────────────────
    // 패턴1: 되감기
    // ──────────────────────────────────────────────

    // threshold 파라미터 버전 (애자일=1, 일반=3)
    public PatternResult detectRewind(String userId, String videoId,
                                      double rewindTargetSec, int threshold) {
        String cooldownKey = cooldownKey(userId, videoId, "rewind");
        if (isCooldown(cooldownKey)) return PatternResult.notTriggered();

        String listKey = "moai:rewind:" + userId + ":" + videoId;
        redisTemplate.opsForList().rightPush(listKey, String.valueOf(rewindTargetSec));
        setTtlIfNew(listKey);

        Long size = redisTemplate.opsForList().size(listKey);
        log.info("rewind 수신 — user={}, video={}, target={}초, size={}",
                userId, videoId, rewindTargetSec, size);
        if (size == null || size < threshold) return PatternResult.notTriggered();

        List<String> recent = redisTemplate.opsForList().range(listKey, -threshold, -1);
        if (recent == null || recent.size() < threshold) return PatternResult.notTriggered();

        double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (String val : recent) {
            double sec = Double.parseDouble(val);
            min = Math.min(min, sec);
            max = Math.max(max, sec);
        }

        // threshold=1이면 같은 구간 비교 불필요 (값 하나라 min==max)
        if (threshold > 1 && (max - min > REWIND_RANGE_SEC || max - min < 1.0)) {
            return PatternResult.notTriggered();
        }

        setCooldown(cooldownKey);
        redisTemplate.delete(listKey);
        log.info("패턴1(되감기) 발동 — user={}, video={}, 구간={}-{}초", userId, videoId, min, max);
        return new PatternResult(true, "rewind");
    }

    // 기존 시그니처 유지 (기본값 3)
    public PatternResult detectRewind(String userId, String videoId, double rewindTargetSec) {
        return detectRewind(userId, videoId, rewindTargetSec, REWIND_THRESHOLD);
    }

    // ──────────────────────────────────────────────
    // 패턴3: 스킵
    // ──────────────────────────────────────────────

    // threshold 파라미터 버전 (애자일=1, 일반=3)
    public PatternResult detectSkip(String userId, String videoId, int threshold) {
        String cooldownKey = cooldownKey(userId, videoId, "skip");
        if (isCooldown(cooldownKey)) return PatternResult.notTriggered();

        String countKey = "moai:skip:" + userId + ":" + videoId;
        Long count = redisTemplate.opsForValue().increment(countKey);
        setTtlIfNew(countKey);

        if (count == null || count < threshold) return PatternResult.notTriggered();

        setCooldown(cooldownKey);
        redisTemplate.delete(countKey);
        log.info("패턴3(스킵) 발동 — user={}, video={}, count={}", userId, videoId, count);
        return new PatternResult(true, "skip");
    }

    // 기존 시그니처 유지 (기본값 3)
    public PatternResult detectSkip(String userId, String videoId) {
        return detectSkip(userId, videoId, SKIP_THRESHOLD);
    }

    // ──────────────────────────────────────────────
    // 패턴4: 2배속
    // ──────────────────────────────────────────────

    public PatternResult detectSpeedUp(String userId, String videoId, int durationSec) {
        String cooldownKey = cooldownKey(userId, videoId, "speedup");
        if (isCooldown(cooldownKey)) return PatternResult.notTriggered();

        String accumKey = "moai:speedup:" + userId + ":" + videoId;
        Long accumulated = redisTemplate.opsForValue().increment(accumKey, durationSec);
        setTtlIfNew(accumKey);

        log.info("패턴4(2배속) Redis 누적 — user={}, video={}, 누적={}초 (임계={}초)",
                userId, videoId, accumulated, SPEEDUP_THRESHOLD_SEC);

        if (accumulated == null || accumulated < SPEEDUP_THRESHOLD_SEC) {
            return PatternResult.notTriggered();
        }

        setCooldown(cooldownKey);
        redisTemplate.delete(accumKey);
        log.info("패턴4(2배속) 발동 — user={}, video={}, 누적={}초", userId, videoId, accumulated);
        return new PatternResult(true, "speedup");
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private String cooldownKey(String userId, String videoId, String pattern) {
        return "moai:cooldown:" + userId + ":" + videoId + ":" + pattern;
    }

    private boolean isCooldown(String cooldownKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey));
    }

    private void setCooldown(String cooldownKey) {
        redisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_TTL);
    }

    private void setTtlIfNew(String key) {
        Long ttl = redisTemplate.getExpire(key);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(key, EVENT_TTL);
        }
    }
}