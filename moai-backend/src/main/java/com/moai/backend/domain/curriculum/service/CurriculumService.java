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

    // 영상 시청이 기여할 수 있는 최대 진척도 (전체 100% 중 40%)
    private static final BigDecimal VIDEO_MAX_CONTRIBUTION = BigDecimal.valueOf(40);

    @Transactional
    public ProgressUpdateResponseDto updateProgress(String email, String roomId, String weekId, ProgressUpdateRequestDto requestDto) {
        LearningRoom room = findRoomByOwner(email, roomId);

        // 1. 해당 주차 진척도 업데이트
        WeeklyCurriculum curriculum = weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // 영상 시청분은 최대 40%까지만 기여한다.
        // 프론트가 전체 영상 시청률(0~100)을 보내면 0~40으로 환산,
        // 이미 40% 이하의 값을 보내면 그대로 사용한다.
        BigDecimal videoRate = requestDto.getCompletionRate().min(VIDEO_MAX_CONTRIBUTION);

        // flipped(+30%) / quiz(+30%) 완료로 쌓인 진척도가 있으면 유지한다.
        // 영상 시청 업데이트가 거꾸로 학습·퀴즈 완료 진척도를 덮어쓰지 않도록 max를 취한다.
        BigDecimal newRate = videoRate.max(curriculum.getCompletionRate());
        curriculum.updateCompletionRate(newRate);

        // 2. 전체 주차의 평균으로 학습실 completionRate 자동 갱신
        List<WeeklyCurriculum> allWeeks = weeklyCurriculumRepository.findByRoomIdOrderByWeekNumber(room.getId());
        BigDecimal average = allWeeks.stream()
                .map(WeeklyCurriculum::getCompletionRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(allWeeks.size()), 2, RoundingMode.HALF_UP);

        room.updateCompletionRate(average);

        return new ProgressUpdateResponseDto(newRate);
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
