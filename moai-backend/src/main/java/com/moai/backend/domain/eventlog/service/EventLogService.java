package com.moai.backend.domain.eventlog.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.eventlog.dto.EventRequestDto;
import com.moai.backend.domain.eventlog.dto.EventResponseDto;
import com.moai.backend.domain.eventlog.service.EventProcessingService.MaterialProcessResult;
import com.moai.backend.domain.eventlog.service.EventProcessingService.QuizProcessResult;
import com.moai.backend.domain.eventlog.service.PatternDetectionService.PatternResult;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventLogService {

    private final PatternDetectionService patternDetectionService;
    private final EventProcessingService eventProcessingService;
    private final UserRepository userRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final WeeklyCurriculumRepository curriculumRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String DEMO_KEYWORD           = "애자일";
    private static final double DEMO_SEGMENT_START     = 2701.0;
    private static final double DEMO_SEGMENT_END       = 2761.0;
    private static final String DEMO_REWIND_DONE_KEY   = "moai:demo:rewind_done:";
    private static final String DEMO_SKIP_DONE_KEY     = "moai:demo:skip_done:";
    private static final int    REWIND_THRESHOLD_DEFAULT = 3;
    private static final int    SKIP_THRESHOLD_DEFAULT   = 3;

    @Transactional
    public EventResponseDto processEvent(String email, String roomId, EventRequestDto request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        LearningRoom room = learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));
        WeeklyCurriculum curriculum = curriculumRepository
                .findByIdAndRoomId(request.getCurriculumId(), room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        Map<String, Object> payload = request.getPayload();
        String videoId = extractString(payload, "video_id");
        String payloadJson = serializePayload(payload);

        return switch (request.getEventType()) {
            case "video_rewind"  -> handleRewind(user, room, curriculum, videoId, payload, payloadJson);
            case "video_pause"   -> handleMaterialTrigger(user, room, curriculum, videoId, payloadJson,
                    "video_pause", extractDouble(payload, "pause_start_sec"));
            case "tab_departure" -> handleMaterialTrigger(user, room, curriculum, videoId, payloadJson,
                    "tab_departure", extractDouble(payload, "departure_sec"));
            case "video_skip"    -> handleSkip(user, room, curriculum, videoId, payload, payloadJson);
            case "video_speed_up"-> handleSpeedUp(user, room, curriculum, videoId, payload, payloadJson);
            default -> throw new CustomException(ErrorCode.EVENT_UNSUPPORTED_TYPE);
        };
    }

    // ──────────────────────────────────────────────
    // 패턴1: 되감기
    // ──────────────────────────────────────────────

    private EventResponseDto handleRewind(User user, LearningRoom room,
                                          WeeklyCurriculum curriculum, String videoId,
                                          Map<String, Object> payload, String payloadJson) {
        double rewindTargetSec = extractDouble(payload, "rewind_target_sec");
        boolean isAgile = isAgileCurriculum(curriculum);

        // 애자일이면 임계값 1, 아니면 기본값 3
        int threshold = isAgile ? 1 : REWIND_THRESHOLD_DEFAULT;
        PatternResult result = patternDetectionService.detectRewind(
                user.getId(), videoId, rewindTargetSec, threshold);

        if (!result.triggered()) return EventResponseDto.notTriggered();

        if (isAgile) {
            rewindTargetSec = DEMO_SEGMENT_START;
            redisTemplate.opsForValue().set(
                    DEMO_REWIND_DONE_KEY + user.getId(), "1", Duration.ofHours(1));
            log.info("애자일 패턴1 발동 — 구간 고정 {}초", rewindTargetSec);
        }

        MaterialProcessResult processResult = eventProcessingService.processRewindPattern(
                user, room, curriculum, videoId, rewindTargetSec, payloadJson);

        return EventResponseDto.builder()
                .aiTriggered(true)
                .eventType("video_rewind")
                .extractedKeywords(processResult.extractedKeywords())
                .materialId(processResult.materialId())
                .build();
    }

    private EventResponseDto handleMaterialTrigger(User user, LearningRoom room,
                                                   WeeklyCurriculum curriculum, String videoId,
                                                   String payloadJson, String eventType,
                                                   double triggerSec) {
        MaterialProcessResult processResult = eventProcessingService.processPauseOrDeparturePattern(
                user, room, curriculum, videoId, eventType, triggerSec, payloadJson);

        return EventResponseDto.builder()
                .aiTriggered(true)
                .eventType(eventType)
                .extractedKeywords(processResult.extractedKeywords())
                .materialId(processResult.materialId())
                .build();
    }

    // ──────────────────────────────────────────────
    // 패턴3: 스킵
    // ──────────────────────────────────────────────

    private EventResponseDto handleSkip(User user, LearningRoom room,
                                        WeeklyCurriculum curriculum, String videoId,
                                        Map<String, Object> payload, String payloadJson) {
        double skipFromSec = extractDouble(payload, "skip_from_sec");
        double skipToSec   = extractDouble(payload, "skip_to_sec");
        boolean isAgile = isAgileCurriculum(curriculum);

        if (isAgile) {
            // 패턴1 완료 전이면 패턴3 막기
            Boolean rewindDone = redisTemplate.hasKey(DEMO_REWIND_DONE_KEY + user.getId());
            if (!Boolean.TRUE.equals(rewindDone)) {
                return EventResponseDto.notTriggered();
            }
            // 패턴3 이미 발동됐으면 막기
            Boolean skipDone = redisTemplate.hasKey(DEMO_SKIP_DONE_KEY + user.getId());
            if (Boolean.TRUE.equals(skipDone)) {
                return EventResponseDto.notTriggered();
            }
        }

        // 애자일이면 임계값 1, 아니면 기본값 3
        int threshold = isAgile ? 1 : SKIP_THRESHOLD_DEFAULT;
        PatternResult result = patternDetectionService.detectSkip(
                user.getId(), videoId, threshold);

        if (!result.triggered()) return EventResponseDto.notTriggered();

        if (isAgile) {
            skipFromSec = DEMO_SEGMENT_START;
            skipToSec   = DEMO_SEGMENT_END;
            redisTemplate.opsForValue().set(
                    DEMO_SKIP_DONE_KEY + user.getId(), "1", Duration.ofHours(1));
            log.info("애자일 패턴3 발동 — 구간 고정 {}-{}초", skipFromSec, skipToSec);
        }

        QuizProcessResult processResult = eventProcessingService.processSkipOrSpeedUpPattern(
                user, room, curriculum, videoId, "video_skip",
                skipFromSec, skipToSec, (int) skipFromSec, payloadJson);

        return EventResponseDto.builder()
                .aiTriggered(true)
                .eventType("video_skip")
                .build();
    }

    // ──────────────────────────────────────────────
    // 패턴4: 2배속
    // ──────────────────────────────────────────────

    private EventResponseDto handleSpeedUp(User user, LearningRoom room,
                                           WeeklyCurriculum curriculum, String videoId,
                                           Map<String, Object> payload, String payloadJson) {
        double speedStartSec = extractDouble(payload, "speed_start_sec");
        int durationSec = extractInt(payload, "duration_sec");

        log.info("2배속 이벤트 수신 — user={}, video={}, speedStartSec={}, durationSec={}",
                user.getId(), videoId, speedStartSec, durationSec);

        PatternResult result = patternDetectionService.detectSpeedUp(
                user.getId(), videoId, durationSec);

        if (!result.triggered()) return EventResponseDto.notTriggered();

        double toSec = speedStartSec + durationSec;
        QuizProcessResult processResult = eventProcessingService.processSkipOrSpeedUpPattern(
                user, room, curriculum, videoId, "video_speed_up",
                speedStartSec, toSec, (int) speedStartSec, payloadJson);

        return EventResponseDto.builder()
                .aiTriggered(true)
                .eventType("video_speed_up")
                .build();
    }

    // ──────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────

    private boolean isAgileCurriculum(WeeklyCurriculum curriculum) {
        return curriculum.getKeywords() != null
                && curriculum.getKeywords().stream()
                .anyMatch(k -> k.contains(DEMO_KEYWORD));
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) throw new CustomException(ErrorCode.INVALID_INPUT);
        return value.toString();
    }

    private double extractDouble(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) throw new CustomException(ErrorCode.INVALID_INPUT);
        return ((Number) value).doubleValue();
    }

    private int extractInt(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) throw new CustomException(ErrorCode.INVALID_INPUT);
        return ((Number) value).intValue();
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("payload 직렬화 실패", e);
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}