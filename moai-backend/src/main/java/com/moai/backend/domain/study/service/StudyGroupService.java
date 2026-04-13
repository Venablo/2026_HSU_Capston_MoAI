package com.moai.backend.domain.study.service;

import com.moai.backend.domain.notification.entity.Notification;
import com.moai.backend.domain.notification.repository.NotificationRepository;
import com.moai.backend.domain.study.dto.StudyGroupDetailResponseDto;
import com.moai.backend.domain.study.dto.SuggestionAcceptResponseDto;
import com.moai.backend.domain.study.dto.SuggestionListResponseDto;
import com.moai.backend.domain.study.dto.SuggestionRejectResponseDto;
import com.moai.backend.domain.study.entity.StudyGroup;
import com.moai.backend.domain.study.entity.StudyMember;
import com.moai.backend.domain.study.entity.StudySuggestion;
import com.moai.backend.domain.study.repository.StudyGroupRepository;
import com.moai.backend.domain.study.repository.StudyMemberRepository;
import com.moai.backend.domain.study.repository.StudySuggestionRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final StudySuggestionRepository studySuggestionRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public List<SuggestionListResponseDto> getSuggestions(String email) {
        User user = findUserByEmail(email);

        List<StudySuggestion> suggestions = studySuggestionRepository
                .findBySuggestedToIdAndStatus(user.getId(), "pending");

        return suggestions.stream().map(suggestion -> {
            StudyGroup group = suggestion.getGroup();

            StudySuggestion partnerSuggestion = findPartnerSuggestion(group.getId(), suggestion.getId());
            User partner = partnerSuggestion.getSuggestedTo();

            return new SuggestionListResponseDto(
                    suggestion.getId(),
                    suggestion.getSuggestedRole(),
                    new SuggestionListResponseDto.PartnerInfo(
                            partner.getNickname(),
                            partner.getProfileImageUrl()),
                    group.getMatchScore(),
                    group.getMatchKeyword(),
                    group.getMatchReason(),
                    suggestion.getStatus());
        }).toList();
    }

    @Transactional
    public SuggestionAcceptResponseDto acceptSuggestion(String email, String suggestionId) {
        User user = findUserByEmail(email);

        StudySuggestion suggestion = studySuggestionRepository
                .findByIdAndSuggestedToId(suggestionId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.SUGGESTION_NOT_FOUND));

        if (!"pending".equals(suggestion.getStatus())) {
            throw new CustomException(ErrorCode.SUGGESTION_ALREADY_RESPONDED);
        }

        suggestion.accept();

        StudyGroup group = suggestion.getGroup();

        List<StudySuggestion> allSuggestions = studySuggestionRepository.findByGroupId(group.getId());
        boolean allAccepted = allSuggestions.stream()
                .allMatch(s -> "accepted".equals(s.getStatus()));

        if (allAccepted) {
            group.activate();
            for (StudySuggestion s : allSuggestions) {
                studyMemberRepository.save(StudyMember.builder()
                        .group(group)
                        .user(s.getSuggestedTo())
                        .role(s.getSuggestedRole())
                        .build());
            }
        }

        StudySuggestion partnerSuggestion = findPartnerSuggestion(group.getId(), suggestionId);

        notificationRepository.save(Notification.builder()
                .user(partnerSuggestion.getSuggestedTo())
                .type("study_accepted")
                .referenceId(group.getId())
                .build());

        // TODO PHASE 8: NotificationService.pushSse() 연동

        return new SuggestionAcceptResponseDto(
                suggestion.getId(),
                group.getId(),
                suggestion.getStatus(),
                group.getStatus());
    }

    @Transactional
    public SuggestionRejectResponseDto rejectSuggestion(String email, String suggestionId) {
        User user = findUserByEmail(email);

        StudySuggestion suggestion = studySuggestionRepository
                .findByIdAndSuggestedToId(suggestionId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.SUGGESTION_NOT_FOUND));

        if (!"pending".equals(suggestion.getStatus())) {
            throw new CustomException(ErrorCode.SUGGESTION_ALREADY_RESPONDED);
        }

        suggestion.reject();
        suggestion.getGroup().disband();

        StudySuggestion partnerSuggestion = findPartnerSuggestion(
                suggestion.getGroup().getId(), suggestionId);

        notificationRepository.save(Notification.builder()
                .user(partnerSuggestion.getSuggestedTo())
                .type("study_rejected")
                .referenceId(suggestion.getId())
                .build());

        // TODO PHASE 8: NotificationService.pushSse() 연동

        return new SuggestionRejectResponseDto(suggestion.getStatus());
    }

    public StudyGroupDetailResponseDto getGroupDetail(String email, String groupId) {
        User user = findUserByEmail(email);

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDY_GROUP_NOT_FOUND));

        List<StudyMember> members = studyMemberRepository.findByGroupId(groupId);
        boolean isMember = members.stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()));

        if (!isMember) {
            boolean hasSuggestion = studySuggestionRepository.findByGroupId(groupId).stream()
                    .anyMatch(s -> s.getSuggestedTo().getId().equals(user.getId()));
            if (!hasSuggestion) {
                throw new CustomException(ErrorCode.STUDY_GROUP_ACCESS_DENIED);
            }
        }

        User partner;
        String partnerRole;

        if (!members.isEmpty()) {
            StudyMember partnerMember = members.stream()
                    .filter(m -> !m.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.STUDY_GROUP_NOT_FOUND));
            partner = partnerMember.getUser();
            partnerRole = partnerMember.getRole();
        } else {
            StudySuggestion partnerSuggestion = studySuggestionRepository.findByGroupId(groupId).stream()
                    .filter(s -> !s.getSuggestedTo().getId().equals(user.getId()))
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.SUGGESTION_NOT_FOUND));
            partner = partnerSuggestion.getSuggestedTo();
            partnerRole = partnerSuggestion.getSuggestedRole();
        }

        boolean isOnline = Boolean.TRUE.equals(redisTemplate.hasKey("RT:" + partner.getEmail()));

        return new StudyGroupDetailResponseDto(
                group.getId(),
                group.getMatchKeyword(),
                group.getStatus(),
                new StudyGroupDetailResponseDto.PartnerDetail(
                        partner.getId(),
                        partner.getNickname(),
                        partner.getProfileImageUrl(),
                        partnerRole,
                        isOnline));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private StudySuggestion findPartnerSuggestion(String groupId, String excludeSuggestionId) {
        return studySuggestionRepository.findByGroupId(groupId).stream()
                .filter(s -> !s.getId().equals(excludeSuggestionId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.SUGGESTION_NOT_FOUND));
    }
}
