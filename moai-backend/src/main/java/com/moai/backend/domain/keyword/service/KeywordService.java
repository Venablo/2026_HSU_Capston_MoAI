package com.moai.backend.domain.keyword.service;

import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.keyword.dto.KeywordListResponseDto;
import com.moai.backend.domain.keyword.entity.UserKeyword;
import com.moai.backend.domain.keyword.repository.UserKeywordRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordService {

    private final UserKeywordRepository userKeywordRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final UserRepository userRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;

    public KeywordListResponseDto getKeywords(String email, String roomId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        List<UserKeyword> keywords = userKeywordRepository.findByUserIdAndRoomId(user.getId(), roomId);

        // 해소(무효화)된 강점 키워드도 노출에서 제외 — 약점 재발로 무효화된 강점이 화면에 남지 않도록.
        List<KeywordListResponseDto.StrengthKeyword> strengths = keywords.stream()
                .filter(uk -> "strength".equals(uk.getKeywordType()))
                .filter(uk -> !Boolean.TRUE.equals(uk.getIsResolved()))
                .map(KeywordListResponseDto.StrengthKeyword::from)
                .toList();

        // 해소된(is_resolved=true) 약점 키워드는 노출에서 제외
        List<KeywordListResponseDto.WeaknessKeyword> weaknesses = keywords.stream()
                .filter(uk -> "weakness".equals(uk.getKeywordType()))
                .filter(uk -> !Boolean.TRUE.equals(uk.getIsResolved()))
                .map(KeywordListResponseDto.WeaknessKeyword::from)
                .toList();

        return new KeywordListResponseDto(strengths, weaknesses);
    }

    public KeywordListResponseDto getKeywordsByCurriculum(String email, String roomId, String weekId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        // 해당 주차 커리큘럼이 이 학습실 소속인지 검증
        weeklyCurriculumRepository.findByIdAndRoomId(weekId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        List<UserKeyword> keywords =
                userKeywordRepository.findByUserIdAndRoomIdAndCurriculumId(user.getId(), roomId, weekId);

        // 해소(무효화)된 강점 키워드도 노출에서 제외 — 약점 재발로 무효화된 강점이 화면에 남지 않도록.
        List<KeywordListResponseDto.StrengthKeyword> strengths = keywords.stream()
                .filter(uk -> "strength".equals(uk.getKeywordType()))
                .filter(uk -> !Boolean.TRUE.equals(uk.getIsResolved()))
                .map(KeywordListResponseDto.StrengthKeyword::from)
                .toList();

        // 해소된(is_resolved=true) 약점 키워드는 노출에서 제외
        List<KeywordListResponseDto.WeaknessKeyword> weaknesses = keywords.stream()
                .filter(uk -> "weakness".equals(uk.getKeywordType()))
                .filter(uk -> !Boolean.TRUE.equals(uk.getIsResolved()))
                .map(KeywordListResponseDto.WeaknessKeyword::from)
                .toList();

        return new KeywordListResponseDto(strengths, weaknesses);
    }
}
