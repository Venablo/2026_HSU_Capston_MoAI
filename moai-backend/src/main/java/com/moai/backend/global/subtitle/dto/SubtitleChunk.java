package com.moai.backend.global.subtitle.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 자막 한 조각(chunk).
 * Python 스크립트가 반환하는 {text, start, duration} 항목을 Java 측에서 사용하기 좋게
 * 변환한 형태이다. start_sec/end_sec 는 소수점 3자리까지 반올림하여 DB 저장과 일치시킨다.
 */
@Getter
@AllArgsConstructor
public class SubtitleChunk {
    private final int chunkIndex;
    private final String text;
    private final BigDecimal startSec;
    private final BigDecimal endSec;

    /**
     * Python 결과 한 항목을 SubtitleChunk 로 변환한다.
     * end_sec 은 start + duration 으로 미리 계산해 호출자가 다시 계산하지 않도록 한다.
     */
    public static SubtitleChunk of(int index, String text, double start, double duration) {
        BigDecimal startSec = BigDecimal.valueOf(start).setScale(3, RoundingMode.HALF_UP);
        BigDecimal endSec = BigDecimal.valueOf(start + duration).setScale(3, RoundingMode.HALF_UP);
        return new SubtitleChunk(index, text, startSec, endSec);
    }
}
