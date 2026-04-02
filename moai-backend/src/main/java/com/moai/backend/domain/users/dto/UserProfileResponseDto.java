package com.moai.backend.domain.users.dto;

import com.moai.backend.domain.users.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class UserProfileResponseDto {
    private String userId;
    private String loginId;
    private String name;
    private String nickname;
    private String email;
    private LocalDate birthDate;
    private String profileImageUrl;
    private List<String> interestKeywords;
    private Boolean studySuggestionEnabled;
    private String themePreference;
    private String status;

    public static UserProfileResponseDto from(User user) {
        return new UserProfileResponseDto(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getNickname(),
                user.getEmail(),
                user.getBirthDate(),
                user.getProfileImageUrl(),
                user.getInterestKeywords(),
                user.getStudySuggestionEnabled(),
                user.getThemePreference(),
                user.getStatus()
        );
    }
}
