package com.moai.backend.domain.learningroom.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.curriculum.service.CurriculumEnrichmentService;
import com.moai.backend.domain.learningroom.dto.LearningRoomCreateRequestDto;
import com.moai.backend.domain.learningroom.dto.LearningRoomCreateResponseDto;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import com.moai.backend.global.llm.LlmRequestDto;
import com.moai.backend.global.llm.LlmService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningRoomService {

    private final LearningRoomRepository learningRoomRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final UserRepository userRepository;
    private final LlmService llmService;
    private final CurriculumEnrichmentService curriculumEnrichmentService;

    @Transactional
    public LearningRoomCreateResponseDto createRoom(String email, LearningRoomCreateRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 1. LearningRoom INSERT
        LearningRoom room = LearningRoom.builder()
                .user(user)
                .subject(requestDto.getSubject())
                .level(requestDto.getLevel())
                .durationWeeks(requestDto.getDurationWeeks())
                .hoursPerDay(requestDto.getHoursPerDay())
                .build();
        learningRoomRepository.save(room);

        // 2. LLM 커리큘럼 생성
        LlmCurriculumResponse llmResponse = generateCurriculum(
                requestDto.getSubject(),
                requestDto.getLevel(),
                requestDto.getDurationWeeks(),
                requestDto.getHoursPerDay().doubleValue()
        );

        // 3. WeeklyCurriculum INSERT x N주
        List<WeeklyCurriculum> savedCurriculums = new ArrayList<>();
        List<LearningRoomCreateResponseDto.CurriculumSummary> summaries =
                llmResponse.getWeeks().stream()
                        .map(week -> {
                            WeeklyCurriculum curriculum = WeeklyCurriculum.builder()
                                    .room(room)
                                    .weekNumber((short) week.getWeekNumber())
                                    .topic(week.getTopic())
                                    .description(week.getDescription())
                                    .build();
                            weeklyCurriculumRepository.save(curriculum);
                            savedCurriculums.add(curriculum);

                            return new LearningRoomCreateResponseDto.CurriculumSummary(
                                    week.getWeekNumber(), week.getTopic()
                            );
                        })
                        .toList();

        // 4. 비동기 enrichment 트리거: 주차별로 영상 추천 → 자막 스크래핑 → 키워드 추출 병렬 실행
        curriculumEnrichmentService.enrichAllWeeks(savedCurriculums);

        return LearningRoomCreateResponseDto.builder()
                .roomId(room.getId())
                .subject(room.getSubject())
                .level(room.getLevel())
                .durationWeeks(room.getDurationWeeks())
                .curriculum(summaries)
                .build();
    }

    private LlmCurriculumResponse generateCurriculum(String subject, String level,
                                                       short durationWeeks, double hoursPerDay) {
        String systemPrompt = "당신은 학습 커리큘럼 설계 전문가입니다. "
                + "주어진 학습 주제, 수준, 기간에 맞는 주차별 커리큘럼을 JSON으로 생성해주세요. "
                + "반드시 아래 JSON 형식만 출력하세요:\n"
                + "{\"weeks\":[{\"week_number\":1,\"topic\":\"주제\",\"description\":\"설명\"}]}";

        String userMessage = String.format(
                "학습 주제: %s\n학습 수준: %s\n총 기간: %d주\n하루 학습 시간: %.1f시간",
                subject, level, durationWeeks, hoursPerDay
        );

        LlmRequestDto request = LlmRequestDto.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();

        return llmService.callJson(request, LlmCurriculumResponse.class);
    }

    @Getter
    @NoArgsConstructor
    static class LlmCurriculumResponse {
        private List<WeekItem> weeks;

        @Getter
        @NoArgsConstructor
        static class WeekItem {
            @JsonProperty("week_number")
            private int weekNumber;
            private String topic;
            private String description;
        }
    }
}
