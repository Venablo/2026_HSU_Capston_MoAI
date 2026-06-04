package com.moai.backend.domain.users.service;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.users.dto.*;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public User join(UserSignUpRequestDto requestDto) {

        // 1. 중복 회원 검증
        validateDuplicateLoginId(requestDto.getLoginId());
        validateDuplicateEmail(requestDto.getEmail());
        validateDuplicateNickname(requestDto.getNickname());

        // 2. 비밀번호 BCrypt 인코딩
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        // 3. DTO → Entity 변환
        User user = requestDto.toEntity(encodedPassword);

        // 4. DB 저장
        userRepository.save(user);

        return user;
    }

    public UserProfileResponseDto getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponseDto.from(user);
    }

    @Transactional
    public UserUpdateResponseDto updateProfile(String email, UserUpdateRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 닉네임 변경 시 중복 검증 (본인의 현재 닉네임과 동일한 경우 제외)
        if (requestDto.getNickname() != null && !requestDto.getNickname().equals(user.getNickname())) {
            validateDuplicateNickname(requestDto.getNickname());
        }

        user.updateProfile(
                requestDto.getNickname(),
                requestDto.getProfileImageUrl(),
                requestDto.getThemePreference()
        );

        return new UserUpdateResponseDto(
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getThemePreference()
        );
    }

    @Transactional
    public KeywordUpdateResponseDto updateKeywords(String email, KeywordUpdateRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        user.updateInterestKeywords(requestDto.getInterestKeywords());

        return new KeywordUpdateResponseDto(user.getInterestKeywords());
    }

    @Transactional
    public void deleteUser(String email, UserDeleteRequestDto requestDto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 이미 탈퇴한 사용자 확인
        if ("inactive".equals(user.getStatus())) {
            throw new CustomException(ErrorCode.USER_ALREADY_INACTIVE);
        }

        // BCrypt 비밀번호 검증
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 논리 삭제
        user.deactivate();

        // Redis에서 RefreshToken 삭제
        redisTemplate.delete("RT:" + email);
    }

    public List<LearningHistoryResponseDto> getLearningHistory(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<LearningRoom> rooms = learningRoomRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return rooms.stream()
                .map(LearningHistoryResponseDto::from)
                .toList();
    }

    private void validateDuplicateLoginId(String loginId) {
        userRepository.findByLoginId(loginId)
                .ifPresent(m -> {
                    throw new CustomException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
                });
    }

    private void validateDuplicateEmail(String email) {
        userRepository.findByEmail(email)
                .ifPresent(m -> {
                    throw new CustomException(ErrorCode.AUTH_DUPLICATE_EMAIL);
                });
    }

    private void validateDuplicateNickname(String nickname) {
        userRepository.findByNickname(nickname)
                .ifPresent(m -> {
                    throw new CustomException(ErrorCode.AUTH_DUPLICATE_NICKNAME);
                });
    }
}
