package com.moai.backend.global.subtitle;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@AllArgsConstructor
public class SubtitleChunkDto {
    private final int chunkIndex;
    private final String text;
    private final BigDecimal startSec;
    private final BigDecimal endSec;

    public static SubtitleChunkDto of(int index, String text, double start, double duration) {
        BigDecimal startSec = BigDecimal.valueOf(start).setScale(3, RoundingMode.HALF_UP);
        BigDecimal endSec = BigDecimal.valueOf(start + duration).setScale(3, RoundingMode.HALF_UP);
        return new SubtitleChunkDto(index, text, startSec, endSec);
    }
}
