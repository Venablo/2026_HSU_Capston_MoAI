package com.moai.backend.domain.curriculum.service;

import com.moai.backend.domain.curriculum.dto.*;
import com.moai.backend.domain.curriculum.entity.CurriculumResource;
import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurriculumService {

    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final UserRepository userRepository;

    public CurriculumListResponseDto getCurriculumList(String email, String roomId) {
        LearningRoom room = findRoomByOwner(email, roomId);

        List<CurriculumListResponseDto.CurriculumSummary> weeks =
                weeklyCurriculumRepository.findByRoomIdOrderByWeekNumber(room.getId()).stream()
                        .map(CurriculumListResponseDto.CurriculumSummary::from)
                        .toList();

        return new CurriculumListResponseDto(weeks);
    }

    public CurriculumDetailResponseDto getCurriculumDetail(String email, String roomId, String weekId) {
        LearningRoom room = findRoomByOwner(email, roomId);

        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        return CurriculumDetailResponseDto.from(curriculum);
    }

    @Transactional
    public ProgressUpdateResponseDto updateProgress(String email, String roomId, String weekId, ProgressUpdateRequestDto requestDto) {
        LearningRoom room = findRoomByOwner(email, roomId);

        // 1. 해당 주차 진척도 업데이트
        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));
        curriculum.updateCompletionRate(requestDto.getCompletionRate());

        // 2. 전체 주차의 평균으로 학습실 completionRate 자동 갱신
        List<WeeklyCurriculum> allWeeks = weeklyCurriculumRepository.findByRoomIdOrderByWeekNumber(room.getId());
        BigDecimal average = allWeeks.stream()
                .map(WeeklyCurriculum::getCompletionRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(allWeeks.size()), 2, RoundingMode.HALF_UP);

        room.updateCompletionRate(average);

        return new ProgressUpdateResponseDto(requestDto.getCompletionRate());
    }

    public RecommendedVideoListResponseDto getRecommendedVideos(String email, String roomId, String weekId) {
        LearningRoom room = findRoomByOwner(email, roomId);

        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // resources에서 youtube 타입만 추출, durationSec/viewCount는 YouTube Data API 연동 전까지 null
        List<RecommendedVideoListResponseDto.VideoSummary> videos;
        if (curriculum.getResources() == null) {
            videos = Collections.emptyList();
        } else {
            videos = curriculum.getResources().stream()
                    .filter(r -> "youtube".equals(r.getType()))
                    .map(r -> new RecommendedVideoListResponseDto.VideoSummary(
                            r.getVideoId(), r.getTitle(), r.getDurationSec(), r.getViewCount()
                    ))
                    .toList();
        }

        return new RecommendedVideoListResponseDto(videos);
    }

    /**
     * 학습실 소유권 검증 후 LearningRoom 반환
     */
    private LearningRoom findRoomByOwner(String email, String roomId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));
    }
}
