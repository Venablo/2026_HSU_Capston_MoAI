package com.moai.backend.domain.study.service;

import com.moai.backend.domain.curriculum.entity.WeeklyCurriculum;
import com.moai.backend.domain.curriculum.repository.WeeklyCurriculumRepository;
import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.study.dto.MatchRequestDto;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private final UserRepository userRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final WeeklyCurriculumRepository weeklyCurriculumRepository;
    private final MatchingEngineService matchingEngineService;

    /**
     * 사용자 트리거 매칭 요청을 검증하고 비동기 매칭 엔진을 실행한다.
     *
     * 검증 순서:
     * 1) email → User 조회 (USER_NOT_FOUND)
     * 2) roomId가 user 소유인지 검증 (LEARNING_ROOM_NOT_FOUND)
     * 3) curriculumId가 roomId 소속인지 검증 (CURRICULUM_NOT_FOUND)
     * 4) studySuggestionEnabled 권한 검증 (STUDY_SUGGESTION_DISABLED)
     *
     * 검증 통과 후 matchingEngineService.tryMatch(...)를 호출해
     * matchingExecutor 풀에서 비동기로 매칭 로직이 실행된다. 결과는
     * SSE 채널(study_match / study_no_candidate)을 통해 통보된다.
     */
    public void requestMatch(String email, String roomId, MatchRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        LearningRoom room = learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));

        WeeklyCurriculum curriculum = weeklyCurriculumRepository
                .findByIdAndRoomId(requestDto.getCurriculumId(), roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CURRICULUM_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getStudySuggestionEnabled())) {
            throw new CustomException(ErrorCode.STUDY_SUGGESTION_DISABLED);
        }

        matchingEngineService.tryMatch(user, room, curriculum);
    }
}
