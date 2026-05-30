package com.moai.backend.global.subtitle.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 자막 스크래핑 에러 코드.
 *
 * Python 스크립트의 종료 코드(exitCode) 및 stderr 의 errorCode 문자열과 1:1 로 매핑된다.
 * - exitCode: Python 스크립트가 종료할 때 사용하는 정수. stderr JSON 파싱이 실패해도
 *   exitCode 만으로 복구할 수 있도록 fromExitCode() 를 통해 역매핑한다.
 * - SCRIPT_TIMEOUT / SCRIPT_EXECUTION_FAILED / INVALID_SCRIPT_OUTPUT 은 Java 측에서만
 *   발생하므로 exitCode 가 없다 (-1 로 표기).
 */
@Getter
public enum SubtitleErrorCode {

    VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "SUB_001", "영상이 존재하지 않거나 삭제되었습니다", 10),
    VIDEO_PRIVATE(HttpStatus.FORBIDDEN, "SUB_002", "비공개 영상은 자막을 가져올 수 없습니다", 11),
    VIDEO_AGE_RESTRICTED(HttpStatus.FORBIDDEN, "SUB_003", "연령 제한 영상은 자막을 가져올 수 없습니다", 12),
    VIDEO_REGION_BLOCKED(HttpStatus.FORBIDDEN, "SUB_004", "지역 제한으로 자막에 접근할 수 없습니다", 13),
    NO_SUBTITLES_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "SUB_005", "이 영상에는 사용 가능한 자막이 없습니다", 20),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "SUB_006", "YouTube 요청 한도 초과. 잠시 후 다시 시도해주세요", 30),
    NETWORK_ERROR(HttpStatus.BAD_GATEWAY, "SUB_007", "YouTube 서버 통신 실패", 31),
    INVALID_VIDEO_ID(HttpStatus.BAD_REQUEST, "SUB_008", "올바르지 않은 영상 ID", 40),

    // Java 측에서만 발생하는 코드 (Python exitCode 없음)
    SCRIPT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "SUB_101", "자막 추출 시간 초과 (30초)", -1),
    SCRIPT_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SUB_102", "Python 스크립트 실행 실패", -1),
    INVALID_SCRIPT_OUTPUT(HttpStatus.INTERNAL_SERVER_ERROR, "SUB_103", "스크립트 출력 파싱 실패", -1),

    // Supadata 전용 코드 (HTTP 401/403 인증 실패, 402 크레딧 소진). Python exitCode 없음.
    SUPADATA_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "SUB_201", "Supadata API 인증 실패 (API 키 확인 필요)", -1),
    SUPADATA_QUOTA_EXCEEDED(HttpStatus.PAYMENT_REQUIRED, "SUB_202", "Supadata API 크레딧이 소진되었습니다", -1),

    UNKNOWN_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SUB_999", "자막 추출 중 알 수 없는 오류", 99);

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
    private final int exitCode;

    SubtitleErrorCode(HttpStatus httpStatus, String code, String message, int exitCode) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
        this.exitCode = exitCode;
    }

    /**
     * Python 종료 코드를 SubtitleErrorCode 로 역매핑한다.
     * stderr JSON 파싱이 실패했을 때의 폴백 경로.
     * 알 수 없는 exitCode 는 UNKNOWN_ERROR 로 분류한다.
     */
    public static SubtitleErrorCode fromExitCode(int exitCode) {
        for (SubtitleErrorCode code : values()) {
            if (code.exitCode == exitCode && code.exitCode >= 0) {
                return code;
            }
        }
        return UNKNOWN_ERROR;
    }

    /**
     * Python stderr JSON 의 errorCode 문자열을 enum 으로 변환한다.
     * 매핑되지 않는 값은 UNKNOWN_ERROR 로 분류한다.
     */
    public static SubtitleErrorCode fromName(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN_ERROR;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNKNOWN_ERROR;
        }
    }
}
