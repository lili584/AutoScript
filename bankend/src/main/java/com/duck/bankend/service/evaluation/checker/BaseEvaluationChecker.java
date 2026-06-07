package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.dto.EvaluationMetric;

abstract class BaseEvaluationChecker implements EvaluationChecker {

    private static final double DEFAULT_THRESHOLD = 80.0;

    protected EvaluationMetric metric(String key, String name, int numerator, int denominator, String summary) {
        double score = denominator == 0 ? 100 : (double) numerator * 100 / denominator;
        return metricWithScore(key, name, score, numerator, denominator, summary);
    }

    protected EvaluationMetric metricWithScore(String key, String name, double score,
                                               int numerator, int denominator, String summary) {
        double normalizedScore = Math.max(0, Math.min(100, score));
        normalizedScore = Math.round(normalizedScore * 10.0) / 10.0;
        return new EvaluationMetric(key, name, normalizedScore, numerator, denominator, summary, DEFAULT_THRESHOLD);
    }

    protected EvaluationIssue issue(String checker, String severity, String type, String message,
                                    String sceneId, Integer chapterIndex, Integer paragraphStart, Integer paragraphEnd,
                                    String yamlText, String novelText, String suggestion) {
        return new EvaluationIssue(checker, severity, type, message, sceneId, chapterIndex, paragraphStart, paragraphEnd, yamlText, novelText, suggestion);
    }
}
