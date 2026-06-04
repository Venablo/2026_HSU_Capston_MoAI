package com.moai.backend.domain.chat.service;

import com.moai.backend.domain.chat.dto.ChatMessageResponseDto;
import com.moai.backend.domain.chat.entity.StudyMessage;
import com.moai.backend.domain.chat.repository.StudyMessageRepository;
import com.moai.backend.domain.notification.dto.SseChatMessageEvent;
import com.moai.backend.domain.notification.entity.Notification;
import com.moai.backend.domain.notification.repository.NotificationRepository;
import com.moai.backend.domain.notification.service.NotificationService;
import com.moai.backend.domain.study.entity.StudyGroup;
import com.moai.backend.domain.study.entity.StudyMember;
import com.moai.backend.domain.study.repository.StudyGroupRepository;
import com.moai.backend.domain.study.repository.StudyMemberRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final StudyMessageRepository studyMessageRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ChatSessionRegistry chatSessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    public List<ChatMessageResponseDto> getMessages(String email, String groupId, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        validateMembership(groupId, user.getId());

        Page<StudyMessage> messages = studyMessageRepository.findByGroupIdOrderBySentAtDesc(groupId, pageable);

        return messages.getContent().stream()
                .map(ChatMessageResponseDto::from)
                .toList();
    }

    @Transactional
    public void sendMessage(String groupId, String email, String content) {
        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        validateMembership(groupId, sender.getId());

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDY_GROUP_NOT_FOUND));

        if ("completed".equals(group.getStatus()) || group.isExpired()) {
            if (group.isExpired() && "active".equals(group.getStatus())) {
                group.complete();
            }
            throw new CustomException(ErrorCode.STUDY_GROUP_EXPIRED);
        }

        StudyMessage message = StudyMessage.builder()
                .group(group)
                .sender(sender)
                .senderType("user")
                .content(content)
                .isAiCorrection(false)
                .build();
        studyMessageRepository.save(message);

        ChatMessageResponseDto responseDto = ChatMessageResponseDto.from(message);
        messagingTemplate.convertAndSend("/sub/chat/" + groupId, responseDto);

        notifyOfflineMembers(groupId, sender, content);
    }

    private void notifyOfflineMembers(String groupId, User sender, String content) {
        List<StudyMember> members = studyMemberRepository.findByGroupId(groupId);
        String preview = content.length() > 50 ? content.substring(0, 50) + "..." : content;

        for (StudyMember member : members) {
            String memberId = member.getUser().getId();
            if (memberId.equals(sender.getId())) {
                continue;
            }

            if (!chatSessionRegistry.isConnected(groupId, memberId)) {
                Notification notification = Notification.builder()
                        .user(member.getUser())
                        .type("chat_message")
                        .message(sender.getNickname() + "님이 메시지를 보냈습니다.")
                        .referenceId(groupId)
                        .build();
                notificationRepository.save(notification);

                SseChatMessageEvent sseEvent = new SseChatMessageEvent(
                        "chat_message",
                        sender.getNickname() + "님이 메시지를 보냈습니다.",
                        groupId,
                        new SseChatMessageEvent.SenderInfo(
                                sender.getNickname(),
                                sender.getProfileImageUrl()),
                        preview);
                notificationService.pushSse(memberId, sseEvent);
            }
        }
    }

    private void validateMembership(String groupId, String userId) {
        if (!studyMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new CustomException(ErrorCode.CHAT_GROUP_ACCESS_DENIED);
        }
    }
}
