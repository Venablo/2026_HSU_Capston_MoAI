package com.moai.backend.domain.user.service;

import com.moai.backend.domain.user.dto.UserSignUpRequestDto;
import com.moai.backend.domain.user.entity.User;
import com.moai.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 주입
@Transactional(readOnly = true) // 기본적으로 조회 성능 최적화
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional // 쓰기 권한 부여(readOnly = false)
    public Long join(UserSignUpRequestDto requestDto) {

        // 1. 중복 회원 검증
        validateDuplicateUser(requestDto.getEmail());

        // 2. DTO에서 꺼낸 비밀번호를 BCrypt로 변환
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        // 3. DTO를 열어서 Entity로 변환
        User user = requestDto.toEntity(requestDto.getPassword());

        // 4. Repository로 DB에 저장
        userRepository.save(user);

        return user.getId(); // 저장된 유저의 고유 번호 반환
    }

    // 중복 검사 로직
    private void validateDuplicateUser(String email) {
        userRepository.findByEmail(email)
                .ifPresent(m -> {
                    throw new IllegalStateException("이미 존재하는 이메일입니다.");
                });
    }
}