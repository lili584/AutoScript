package com.duck.bankend.model.evaluation;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.dto.EvaluationMetric;

import java.util.List;

public record EvaluationCheckResult(
        EvaluationMetric metric,
        List<EvaluationIssue> issues
) {
}
