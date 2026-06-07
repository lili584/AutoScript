package com.duck.bankend.service.evaluation;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.dto.EvaluationMetric;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 将测评结果格式化为 Markdown，供前端展示或下载。
 */
@Component
public class EvaluationMarkdownReporter {

    public String build(double overall, boolean passed, List<EvaluationMetric> metrics, List<EvaluationIssue> issues) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 剧本 YAML 测评报告\n\n");
        markdown.append("## 总分\n\n");
        markdown.append("- overall_score: ").append(overall).append("\n");
        markdown.append("- passed: ").append(passed).append("\n\n");
        markdown.append("## Scorecard\n\n");
        markdown.append("| 指标 | 分数 | 阈值 | 通过/总数 | 摘要 |\n");
        markdown.append("| --- | ---: | ---: | ---: | --- |\n");
        for (EvaluationMetric metric : metrics) {
            markdown.append("| ")
                    .append(metric.getName())
                    .append(" | ")
                    .append(metric.getScore())
                    .append(" | ")
                    .append(metric.getThreshold())
                    .append(" | ")
                    .append(metric.getNumerator())
                    .append("/")
                    .append(metric.getDenominator())
                    .append(" | ")
                    .append(metric.getSummary())
                    .append(" |\n");
        }
        markdown.append("\n## Issues\n\n");
        if (issues.isEmpty()) {
            markdown.append("未发现明显问题。\n");
            return markdown.toString();
        }
        for (EvaluationIssue issue : issues) {
            markdown.append("- [")
                    .append(issue.getSeverity())
                    .append("] ")
                    .append(issue.getType())
                    .append(": ")
                    .append(issue.getMessage());
            if (StringUtils.hasText(issue.getSceneId())) {
                markdown.append(" scene=").append(issue.getSceneId());
            }
            if (issue.getChapterIndex() != null) {
                markdown.append(" chapter=").append(issue.getChapterIndex());
            }
            if (StringUtils.hasText(issue.getYamlText())) {
                markdown.append(" yaml=\"").append(issue.getYamlText()).append("\"");
            }
            if (StringUtils.hasText(issue.getSuggestion())) {
                markdown.append(" suggestion=").append(issue.getSuggestion());
            }
            markdown.append("\n");
        }
        return markdown.toString();
    }
}
