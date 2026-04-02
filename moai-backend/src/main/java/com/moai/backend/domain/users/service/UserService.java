package com.moai.backend.domain.users.service;

import com.moai.backend.domain.users.dto.UserProfileResponseDto;
import com.moai.backend.domain.users.dto.UserSignUpRequestDto;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
