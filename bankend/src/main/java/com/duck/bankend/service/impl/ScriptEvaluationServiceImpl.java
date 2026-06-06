package com.duck.bankend.service.impl;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.dto.EvaluationMetric;
import com.duck.bankend.model.dto.EvaluationReport;
import com.duck.bankend.model.dto.EvaluationScorecard;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.NovelEvaluationData;
import com.duck.bankend.model.evaluation.ScriptYamlData;
import com.duck.bankend.model.evaluation.YamlBeatData;
import com.duck.bankend.model.evaluation.YamlCharacterData;
import com.duck.bankend.model.evaluation.YamlDialogueData;
import com.duck.bankend.service.NovelService;
import com.duck.bankend.service.ScriptEvaluationService;
import com.duck.bankend.service.evaluation.NovelEvaluationParser;
import com.duck.bankend.service.evaluation.ScriptYamlEvaluationParser;
import com.duck.bankend.service.evaluation.checker.EvaluationChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ScriptEvaluationServiceImpl implements ScriptEvaluationService {

    private static final List<String> METRIC_ORDER = List.of(
            "dialogue_recall",
            "dialogue_precision",
            "action_coverage",
            "character_consistency",
            "fidelity",
            "structure"
    );

    private final NovelService novelService;
    private final NovelEvaluationParser novelEvaluationParser;
    private final ScriptYamlEvaluationParser scriptYamlEvaluationParser;
    private final List<EvaluationChecker> checkers;

    @Override
    public EvaluationReport evaluateYaml(Long novelId, MultipartFile file) throws IOException {
        Novel novel = novelService.getActiveNovel(novelId);
        if (novel == null) {
            return null;
        }
        validateYamlFile(file);
        String yamlText = new String(file.getBytes(), StandardCharsets.UTF_8);
        NovelEvaluationData novelData = novelEvaluationParser.parse(novel);
        ScriptYamlData yamlData = scriptYamlEvaluationParser.parse(yamlText);
        EvaluationContext context = buildContext(novelData, yamlData);

        List<CompletableFuture<EvaluationCheckResult>> futures = checkers.stream()
                .map(checker -> CompletableFuture.supplyAsync(() -> checker.check(context)))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<EvaluationCheckResult> checkResults = futures.stream().map(CompletableFuture::join).toList();

        List<EvaluationMetric> metrics = checkResults.stream()
                .map(EvaluationCheckResult::metric)
                .sorted(Comparator.comparingInt(metric -> metricOrder(metric.getKey())))
                .toList();
        List<EvaluationIssue> issues = checkResults.stream()
                .flatMap(result -> result.issues().stream())
                .sorted(this::compareIssue)
                .toList();
        double overall = metrics.stream().mapToDouble(EvaluationMetric::getScore).average().orElse(100);
        overall = Math.round(overall * 10.0) / 10.0;

        EvaluationReport report = new EvaluationReport();
        report.setNovelId(novelId);
        report.setYamlFileName(file.getOriginalFilename());
        report.setOverallScore(overall);
        report.setScorecard(new EvaluationScorecard(metrics));
        report.setIssues(issues);
        report.setIssuesMarkdown(buildIssuesMarkdown(overall, metrics, issues));
        return report;
    }

    private EvaluationContext buildContext(NovelEvaluationData novelData, ScriptYamlData yamlData) {
        Map<String, YamlCharacterData> charactersById = new LinkedHashMap<>();
        for (YamlCharacterData character : yamlData.characters()) {
            if (StringUtils.hasText(character.id())) {
                charactersById.put(character.id(), character);
            }
        }
        List<YamlDialogueData> yamlDialogues = new ArrayList<>();
        yamlData.scenes().forEach(scene -> {
            for (YamlBeatData beat : scene.beats()) {
                if ("dialogue".equals(beat.type()) && StringUtils.hasText(beat.text())) {
                    yamlDialogues.add(new YamlDialogueData(beat.text(), scene.id(), beat.characterId(), beat.characterName(), scene.chapterIndex()));
                }
            }
        });
        Set<Integer> novelChapterIndexes = new LinkedHashSet<>();
        novelData.chapters().forEach(chapter -> novelChapterIndexes.add(chapter.index()));
        return new EvaluationContext(novelData, yamlData, yamlDialogues, charactersById, novelChapterIndexes);
    }

    private void validateYamlFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("YAML 文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || (!filename.toLowerCase().endsWith(".yaml") && !filename.toLowerCase().endsWith(".yml"))) {
            throw new IllegalArgumentException("只支持上传 .yaml 或 .yml 文件");
        }
    }

    private int metricOrder(String key) {
        int index = METRIC_ORDER.indexOf(key);
        return index < 0 ? METRIC_ORDER.size() : index;
    }

    private int compareIssue(EvaluationIssue left, EvaluationIssue right) {
        int severity = Integer.compare(severityOrder(left.getSeverity()), severityOrder(right.getSeverity()));
        if (severity != 0) {
            return severity;
        }
        int chapter = Integer.compare(nullLast(left.getChapterIndex()), nullLast(right.getChapterIndex()));
        if (chapter != 0) {
            return chapter;
        }
        return String.valueOf(left.getSceneId()).compareTo(String.valueOf(right.getSceneId()));
    }

    private int severityOrder(String severity) {
        return switch (String.valueOf(severity)) {
            case "error" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private int nullLast(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private String buildIssuesMarkdown(double overall, List<EvaluationMetric> metrics, List<EvaluationIssue> issues) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 剧本 YAML 测评报告\n\n");
        markdown.append("## 总分\n\n");
        markdown.append("- overall_score: ").append(overall).append("\n\n");
        markdown.append("## Scorecard\n\n");
        markdown.append("| 指标 | 分数 | 通过/总数 | 摘要 |\n");
        markdown.append("| --- | ---: | ---: | --- |\n");
        for (EvaluationMetric metric : metrics) {
            markdown.append("| ")
                    .append(metric.getName())
                    .append(" | ")
                    .append(metric.getScore())
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
