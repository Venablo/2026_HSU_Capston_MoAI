package com.moai.backend.domain.quiz.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.quiz.dto.QuizAttemptDetailResponseDto;
import com.moai.backend.domain.quiz.dto.QuizAttemptListResponseDto;
import com.moai.backend.domain.quiz.entity.QuizAttempt;
import com.moai.backend.domain.quiz.repository.QuizAttemptRepository;
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
public class QuizService {

    private final QuizAttemptRepository quizAttemptRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final UserRepository userRepository;

    public QuizAttemptListResponseDto getQuizAttempts(String email, String roomId, String weekId) {
        User user = findUserByEmail(email);
        LearningRoom room = findRoomByOwner(user, roomId);

        // 해당 주차가 학습실에 속하는지 검증
        weeklyCurriculumRepository.findByIdAndRoomId(weekId, room.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        // quiz → curriculum 조인을 통해 주차별 응시 이력 조회
        List<QuizAttemptListResponseDto.AttemptSummary> attempts =
                quizAttemptRepository.findByUserIdAndQuiz_CurriculumIdOrderByAttemptedAtDesc(
                        user.getId(), weekId
                ).stream()
                        .map(QuizAttemptListResponseDto.AttemptSummary::from)
                        .toList();

        return new QuizAttemptListResponseDto(attempts);
    }

    public QuizAttemptDetailResponseDto getQuizAttemptDetail(String email, String attemptId) {
        User user = findUserByEmail(email);

        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND));

        // 본인의 응시 기록만 조회 가능
        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.QUIZ_ATTEMPT_NOT_FOUND);
        }

        return QuizAttemptDetailResponseDto.from(attempt);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 학습실 소유권 검증 후 LearningRoom 반환
     */
    private LearningRoom findRoomByOwner(User user, String roomId) {
        return learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));
    }
}
