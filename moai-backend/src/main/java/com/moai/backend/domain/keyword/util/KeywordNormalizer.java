package com.moai.backend.domain.keyword.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LLM 이 반환한 키워드를 커리큘럼 핵심 키워드의 "원형" 표기로 정규화한다.
 *
 * 예) 커리큘럼에 "정규화(Normalization)" 가 있을 때:
 *   - LLM "normalization"         → "정규화(Normalization)"
 *   - LLM "정규화"                → "정규화(Normalization)"
 *   - LLM "정규화(normalization)" → "정규화(Normalization)"
 *   - 커리큘럼에 없는 키워드는 결과에서 제외
 *
 * 약점/강점 저장 경로 전체에서 동일 정규화를 거쳐야 같은 개념이 표기 차이로
 * 여러 키로 분산 카운팅되거나 매칭 엔진에서 못 만나는 문제를 막을 수 있다.
 */
public final class KeywordNormalizer {

    private static final Pattern KOR_ENG_PAREN_PATTERN =
            Pattern.compile("^([^()]+?)\\s*\\(([^()]+)\\)$");

    private KeywordNormalizer() {}

    /**
     * 커리큘럼 키워드 리스트로 "변형 lowercase → 원형" 역인덱스를 만든다.
     */
    public static Map<String, String> buildVariantIndex(List<String> curriculumKeywords) {
        if (curriculumKeywords == null || curriculumKeywords.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> index = new HashMap<>();
        for (String original : curriculumKeywords) {
            if (original == null || original.isBlank()) continue;
            String lower = original.toLowerCase().trim();
            index.putIfAbsent(lower, original);
            Matcher m = KOR_ENG_PAREN_PATTERN.matcher(lower);
            if (m.matches()) {
                String left = m.group(1).trim();
                String right = m.group(2).trim();
                if (!left.isEmpty()) index.putIfAbsent(left, original);
                if (!right.isEmpty()) index.putIfAbsent(right, original);
            }
        }
        return index;
    }

    /**
     * LLM 추출 키워드를 커리큘럼 원형으로 정규화한다.
     * - 변형 매칭 → 커리큘럼 원형으로 치환
     * - 매칭 실패 → 결과에서 제외
     * - 중복 제거
     */
    public static List<String> normalize(List<String> llmKeywords, List<String> curriculumKeywords) {
        if (llmKeywords == null || llmKeywords.isEmpty()) return Collections.emptyList();
        Map<String, String> index = buildVariantIndex(curriculumKeywords);
        if (index.isEmpty()) return Collections.emptyList();
        return llmKeywords.stream()
                .filter(Objects::nonNull)
                .map(k -> index.get(k.toLowerCase().trim()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}
