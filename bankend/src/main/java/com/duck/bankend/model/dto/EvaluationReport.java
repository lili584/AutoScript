package com.duck.bankend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationReport {

    private Long novelId;
    private String yamlFileName;
    private Double overallScore;
    private Boolean passed;
    private EvaluationScorecard scorecard;
    private List<EvaluationIssue> issues;
    private String issuesMarkdown;
}
