package com.moai.backend.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(400, "COMMON_001", "입력값이 올바르지 않습니다."),

    // Auth
    AUTH_INVALID_CREDENTIALS(400, "AUTH_001", "이메일 또는 비밀번호가 일치하지 않습니다."),
    AUTH_DUPLICATE_LOGIN_ID(409, "AUTH_006", "이미 사용 중인 로그인 아이디입니다."),
    AUTH_DUPLICATE_EMAIL(409, "AUTH_002", "이미 가입된 이메일입니다."),
    AUTH_DUPLICATE_NICKNAME(409, "AUTH_003", "이미 사용 중인 닉네임입니다."),
    AUTH_INVALID_TOKEN(401, "AUTH_004", "유효하지 않거나 이미 로그아웃된 토큰입니다."),
    AUTH_TOKEN_MISMATCH(401, "AUTH_005", "토큰 정보가 일치하지 않습니다."),

    // User
    USER_NOT_FOUND(404, "USER_001", "사용자를 찾을 수 없습니다."),

    // LLM
    LLM_API_CALL_FAILED(502, "LLM_001", "LLM API 호출에 실패했습니다."),
    LLM_RESPONSE_PARSE_ERROR(502, "LLM_002", "LLM 응답을 파싱할 수 없습니다."),

    // Learning Room
    LEARNING_ROOM_NOT_FOUND(404, "ROOM_001", "학습실을 찾을 수 없습니다."),

    // Curriculum
    CURRICULUM_NOT_FOUND(404, "CURRICULUM_001", "주차 커리큘럼을 찾을 수 없습니다."),
    WEEK_LOCKED(403, "CURRICULUM_002", "아직 잠긴 주차입니다. 이전 주차를 완료해야 합니다."),

    // Material
    MATERIAL_NOT_FOUND(404, "MATERIAL_001", "학습 자료를 찾을 수 없습니다."),

    // Quiz
    QUIZ_ATTEMPT_NOT_FOUND(404, "QUIZ_001", "퀴즈 응시 기록을 찾을 수 없습니다."),
    QUIZ_NOT_FOUND(404, "QUIZ_002", "퀴즈를 찾을 수 없습니다."),
    QUIZ_QUESTION_NOT_FOUND(404, "QUIZ_003", "퀴즈 문항을 찾을 수 없습니다."),

    // Event
    EVENT_UNSUPPORTED_TYPE(400, "EVENT_001", "지원하지 않는 이벤트 타입입니다."),
    EVENT_TRANSCRIPT_NOT_FOUND(404, "EVENT_002", "해당 구간의 자막을 찾을 수 없습니다."),

    // Final Quiz
    FINAL_QUIZ_NOT_READY(400, "QUIZ_004", "거꾸로 학습을 완료해야 파이널 퀴즈를 시작할 수 있습니다."),
    QUIZ_REPORT_NOT_FOUND(404, "QUIZ_005", "퀴즈 리포트를 찾을 수 없습니다."),
    FINAL_QUIZ_ALREADY_SUBMITTED(409, "QUIZ_006", "이미 파이널 퀴즈를 제출하였습니다."),

    // Flipped Learning
    FLIPPED_SESSION_NOT_FOUND(404, "FLIPPED_001", "거꾸로 학습 세션을 찾을 수 없습니다."),
    FLIPPED_VIDEO_NOT_COMPLETED(400, "FLIPPED_002", "영상 시청을 완료해야 거꾸로 학습을 시작할 수 있습니다."),
    FLIPPED_SESSION_ALREADY_COMPLETED(409, "FLIPPED_003", "이미 해당 주차의 거꾸로 학습을 완료하였습니다."),

    // Study Matching
    SUGGESTION_NOT_FOUND(404, "STUDY_001", "스터디 제안을 찾을 수 없습니다."),
    SUGGESTION_ALREADY_RESPONDED(409, "STUDY_002", "이미 응답한 스터디 제안입니다."),
    STUDY_GROUP_NOT_FOUND(404, "STUDY_003", "스터디 그룹을 찾을 수 없습니다."),
    STUDY_GROUP_ACCESS_DENIED(403, "STUDY_004", "스터디 그룹에 접근 권한이 없습니다."),
    STUDY_GROUP_EXPIRED(403, "STUDY_005", "스터디 기간이 만료되었습니다."),

    // Chat
    CHAT_GROUP_ACCESS_DENIED(403, "CHAT_001", "채팅방에 접근 권한이 없습니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(404, "NOTI_001", "알림을 찾을 수 없습니다."),
    NOTIFICATION_ACCESS_DENIED(403, "NOTI_002", "알림에 접근 권한이 없습니다."),

    // User - 회원 탈퇴
    PASSWORD_MISMATCH(400, "USER_002", "비밀번호가 일치하지 않습니다."),
    USER_ALREADY_INACTIVE(409, "USER_003", "이미 탈퇴한 사용자입니다."),
    USER_INACTIVE(403, "USER_004", "탈퇴한 사용자입니다."),

    // S3
    S3_UPLOAD_FAILED(500, "S3_001", "파일 업로드에 실패했습니다."),
    S3_PRESIGN_FAILED(500, "S3_002", "Presigned URL 생성에 실패했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
