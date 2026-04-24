package com.moai.backend.domain.eventlog.dto;

import com.moai.backend.domain.material.entity.SummaryItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LlmSummaryResult {

    private String title;
    private List<SummaryItem> summaryItems;
}
