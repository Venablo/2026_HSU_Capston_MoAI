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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 이벤트 처리 메인 진입점.
     * 1) 사용자·학습실·주차 검증
     * 2) event_type별 Redis 패턴 감지
     * 3) 패턴 발동 시 AI 처리 서비스 호출
     */
    @Transactional
    public EventResponseDto processEvent(String email, String roomId, EventRequestDto request) {
        // 사용자 및 학습실 소유권 검증
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        LearningRoom room = learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        // 주차 커리큘럼 조회 및 학습실 소속 검증
        WeeklyCurriculum curriculum = curriculumRepository
                .findByIdAndRoomId(request.getCurriculumId(), room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        Map<String, Object> payload = request.getPayload();
        String videoId = extractString(payload, "video_id");
        String payloadJson = serializePayload(payload);

        // 이벤트 타입별 분기 처리
        return switch (request.getEventType()) {
            case "video_rewind" -> handleRewind(user, room, curriculum, videoId, payload, payloadJson);
            case "video_pause" -> handleMaterialTrigger(user, room, curriculum, videoId, payloadJson,
                    "video_pause", extractDouble(payload, "pause_start_sec"));
            case "tab_departure" -> handleMaterialTrigger(user, room, curriculum, videoId, payloadJson,
                    "tab_departure", extractDouble(payload, "departure_sec"));
            case "video_skip" -> handleSkip(user, room, curriculum, videoId, payload, payloadJson);
            case "video_speed_up" -> handleSpeedUp(user, room, curriculum, videoId, payload, payloadJson);
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

        // Redis에서 되감기 패턴 감지 (3회 같은 구간 반복 여부)
        PatternResult result = patternDetectionService.detectRewind(
                user.getId(), videoId, rewindTargetSec);

        if (!result.triggered()) {
            return EventResponseDto.notTriggered();
        }

        // 패턴 발동 → AI 요약 자료 생성 + 약점 키워드 저장
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
        double skipToSec = extractDouble(payload, "skip_to_sec");

        // Redis에서 스킵 패턴 감지 (3회 누적 여부)
        PatternResult result = patternDetectionService.detectSkip(user.getId(), videoId);

        if (!result.triggered()) {
            return EventResponseDto.notTriggered();
        }

        // 패턴 발동 → 돌발 퀴즈 생성 (rewindToSec = 스킵 시작 지점)
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

        // Redis에서 2배속 누적 감지 (180초 이상 여부)
        PatternResult result = patternDetectionService.detectSpeedUp(
                user.getId(), videoId, durationSec);

        if (!result.triggered()) {
            return EventResponseDto.notTriggered();
        }

        // 패턴 발동 → 돌발 퀴즈 생성 (rewindToSec = 2배속 시작 지점)
        // fromSec~toSec: 2배속 구간 (speed_start_sec ~ speed_start_sec + duration_sec)
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
    // payload 값 추출 헬퍼
    // ──────────────────────────────────────────────

    private String extractString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return value.toString();
    }

    private double extractDouble(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ((Number) value).doubleValue();
    }

    private int extractInt(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
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
