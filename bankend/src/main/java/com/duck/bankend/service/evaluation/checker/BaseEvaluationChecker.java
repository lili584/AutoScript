package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.dto.EvaluationMetric;

abstract class BaseEvaluationChecker implements EvaluationChecker {

    protected EvaluationMetric metric(String key, String name, int numerator, int denominator, String summary) {
        double score = denominator == 0 ? 100 : Math.max(0, Math.min(100, (double) numerator * 100 / denominator));
        score = Math.round(score * 10.0) / 10.0;
        return new EvaluationMetric(key, name, score, numerator, denominator, summary);
    }

    protected EvaluationIssue issue(String checker, String severity, String type, String message,
                                    String sceneId, Integer chapterIndex, Integer paragraphStart, Integer paragraphEnd,
                                    String yamlText, String novelText, String suggestion) {
        return new EvaluationIssue(checker, severity, type, message, sceneId, chapterIndex, paragraphStart, paragraphEnd, yamlText, novelText, suggestion);
    }
}
