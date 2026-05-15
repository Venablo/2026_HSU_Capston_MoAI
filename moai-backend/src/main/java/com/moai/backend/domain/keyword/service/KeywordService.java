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

        List<KeywordListResponseDto.StrengthKeyword> strengths = keywords.stream()
                .filter(uk -> "strength".equals(uk.getKeywordType()))
                .map(KeywordListResponseDto.StrengthKeyword::from)
                .toList();

        List<KeywordListResponseDto.WeaknessKeyword> weaknesses = keywords.stream()
                .filter(uk -> "weakness".equals(uk.getKeywordType()))
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

        List<KeywordListResponseDto.StrengthKeyword> strengths = keywords.stream()
                .filter(uk -> "strength".equals(uk.getKeywordType()))
                .map(KeywordListResponseDto.StrengthKeyword::from)
                .toList();

        List<KeywordListResponseDto.WeaknessKeyword> weaknesses = keywords.stream()
                .filter(uk -> "weakness".equals(uk.getKeywordType()))
                .map(KeywordListResponseDto.WeaknessKeyword::from)
                .toList();

        return new KeywordListResponseDto(strengths, weaknesses);
    }
}
