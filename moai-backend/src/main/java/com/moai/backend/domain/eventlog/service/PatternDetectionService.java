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

    private static final Duration EVENT_TTL = Duration.ofMinutes(10);
    private static final Duration COOLDOWN_TTL = Duration.ofMinutes(5);

    // 패턴1 되감기 발동 임계값: 3회 이상 같은 구간 반복
    private static final int REWIND_THRESHOLD = 3;
    // 되감기 "같은 구간" 판정 허용 범위(초)
    private static final double REWIND_RANGE_SEC = 10.0;

    // 패턴3 스킵 발동 임계값: 3회
    private static final int SKIP_THRESHOLD = 3;

    // 패턴4 2배속 발동 임계값: 누적 180초(3분)
    private static final int SPEEDUP_THRESHOLD_SEC = 180;

    /**
     * 패턴 감지 결과를 담는 레코드
     * @param triggered 패턴 발동 여부
     * @param patternType 발동된 패턴 종류 (rewind / skip / speedup)
     */
    public record PatternResult(boolean triggered, String patternType) {
        public static PatternResult notTriggered() {
            return new PatternResult(false, null);
        }
    }

    // ──────────────────────────────────────────────
    // 패턴1: 되감기 감지
    // ──────────────────────────────────────────────

    /**
     * 되감기 이벤트를 Redis 리스트에 누적하고 패턴 발동 여부를 판단한다.
     * - RPUSH로 되감기 대상 초를 리스트에 추가
     * - 리스트 길이 ≥ 3이면 최근 3개의 MAX - MIN이 10초 이내인지 검사
     * - 같은 구간 반복으로 판정되면 쿨다운 설정 후 발동
     */
    public PatternResult detectRewind(String userId, String videoId, double rewindTargetSec) {
        String cooldownKey = cooldownKey(userId, videoId, "rewind");

        // 쿨다운 중이면 발동하지 않음
        if (isCooldown(cooldownKey)) {
            return PatternResult.notTriggered();
        }

        String listKey = "moai:rewind:" + userId + ":" + videoId;

        // 되감기 대상 초를 리스트 끝에 추가
        redisTemplate.opsForList().rightPush(listKey, String.valueOf(rewindTargetSec));
        setTtlIfNew(listKey);

        Long size = redisTemplate.opsForList().size(listKey);
        if (size == null || size < REWIND_THRESHOLD) {
            return PatternResult.notTriggered();
        }

        // 최근 3개 값을 꺼내서 같은 구간인지 판정 (MAX - MIN ≤ 10초)
        List<String> recent = redisTemplate.opsForList().range(listKey, -REWIND_THRESHOLD, -1);
        if (recent == null || recent.size() < REWIND_THRESHOLD) {
            return PatternResult.notTriggered();
        }

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (String val : recent) {
            double sec = Double.parseDouble(val);
            min = Math.min(min, sec);
            max = Math.max(max, sec);
        }

        if (max - min > REWIND_RANGE_SEC) {
            return PatternResult.notTriggered();
        }

        // 패턴 발동: 쿨다운 설정 후 리스트 초기화
        setCooldown(cooldownKey);
        redisTemplate.delete(listKey);

        log.info("패턴1(되감기) 발동 — user={}, video={}, 구간={}-{}초", userId, videoId, min, max);
        return new PatternResult(true, "rewind");
    }

    // ──────────────────────────────────────────────
    // 패턴3: 스킵 감지
    // ──────────────────────────────────────────────

    /**
     * 스킵 이벤트 횟수를 Redis에서 INCR로 카운팅하고, 임계값 도달 시 발동한다.
     */
    public PatternResult detectSkip(String userId, String videoId) {
        String cooldownKey = cooldownKey(userId, videoId, "skip");

        if (isCooldown(cooldownKey)) {
            return PatternResult.notTriggered();
        }

        String countKey = "moai:skip:" + userId + ":" + videoId;

        Long count = redisTemplate.opsForValue().increment(countKey);
        setTtlIfNew(countKey);

        if (count == null || count < SKIP_THRESHOLD) {
            return PatternResult.notTriggered();
        }

        // 패턴 발동: 쿨다운 설정 후 카운트 초기화
        setCooldown(cooldownKey);
        redisTemplate.delete(countKey);

        log.info("패턴3(스킵) 발동 — user={}, video={}, count={}", userId, videoId, count);
        return new PatternResult(true, "skip");
    }

    // ──────────────────────────────────────────────
    // 패턴4: 2배속 누적 감지
    // ──────────────────────────────────────────────

    /**
     * 2배속 시청 시간을 Redis에서 INCRBY로 누적하고, 180초(3분) 도달 시 발동한다.
     * 프론트에서 2배속 시청 중 10초마다 duration_sec를 전송하며,
     * 2배속 해제/영상 종료 시 잔여 초도 마지막으로 전송한다.
     */
    public PatternResult detectSpeedUp(String userId, String videoId, int durationSec) {
        String cooldownKey = cooldownKey(userId, videoId, "speedup");

        if (isCooldown(cooldownKey)) {
            return PatternResult.notTriggered();
        }

        String accumKey = "moai:speedup:" + userId + ":" + videoId;

        Long accumulated = redisTemplate.opsForValue().increment(accumKey, durationSec);
        setTtlIfNew(accumKey);

        if (accumulated == null || accumulated < SPEEDUP_THRESHOLD_SEC) {
            return PatternResult.notTriggered();
        }

        // 패턴 발동: 쿨다운 설정 후 누적 키 초기화
        setCooldown(cooldownKey);
        redisTemplate.delete(accumKey);

        log.info("패턴4(2배속) 발동 — user={}, video={}, 누적={}초", userId, videoId, accumulated);
        return new PatternResult(true, "speedup");
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼 메서드
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

    /**
     * 키에 TTL이 아직 설정되지 않은 경우(새로 생성된 키)에만 TTL을 부여한다.
     * INCR/RPUSH는 키가 없으면 자동 생성하지만 TTL은 설정하지 않으므로,
     * TTL이 -1(만료 없음)인 경우에만 10분 TTL을 설정한다.
     */
    private void setTtlIfNew(String key) {
        Long ttl = redisTemplate.getExpire(key);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(key, EVENT_TTL);
        }
    }
}
