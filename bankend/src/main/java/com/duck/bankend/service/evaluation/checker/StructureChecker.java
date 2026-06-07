package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.YamlSceneData;
import com.duck.bankend.util.EvaluationTextUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StructureChecker extends BaseEvaluationChecker {

    @Override
    public EvaluationCheckResult check(EvaluationContext context) {
        List<EvaluationIssue> issues = new ArrayList<>();
        Map<String, List<YamlSceneData>> scenesByTitle = new LinkedHashMap<>();
        Map<Integer, List<YamlSceneData>> scenesByChapter = new LinkedHashMap<>();
        Set<Integer> yamlChapterIndexes = new LinkedHashSet<>();
        int healthyScenes = 0;

        for (YamlSceneData scene : context.yaml().scenes()) {
            scenesByTitle.computeIfAbsent(EvaluationTextUtil.normalize(scene.title()), ignored -> new ArrayList<>()).add(scene);
            scenesByChapter.computeIfAbsent(scene.chapterIndex(), ignored -> new ArrayList<>()).add(scene);
            yamlChapterIndexes.add(scene.chapterIndex());

            boolean idOk = scene.id() != null && scene.id().matches("chapter-\\d+-chunk-\\d+-.+");
            boolean beatsOk = scene.beats().size() >= 3;
            boolean hasDialogue = scene.beats().stream().anyMatch(beat -> "dialogue".equals(beat.type()));
            if (idOk && beatsOk && hasDialogue) {
                healthyScenes++;
            }

            if (!idOk) {
                issues.add(issue("Structure", "warning", "scene_id 格式异常",
                        "scene_id 不符合 chapter-{n}-chunk-{n}-... 格式",
                        scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                        scene.id(), null, "按生成规范修正 scene_id"));
            }
            if (!beatsOk) {
                issues.add(issue("Structure", "warning", "场景 beat 数过少",
                        "场景 beats 少于 3 条，可能缺少动作或对白",
                        scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                        String.valueOf(scene.beats().size()), null, "补充必要 beat"));
            }
            if (!hasDialogue) {
                issues.add(issue("Structure", "info", "纯动作场景无对白",
                        "场景没有 dialogue beat，如不是纯动作场景请补充对白",
                        scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                        null, null, "核对该场景是否应包含对白"));
            }
        }

        for (Map.Entry<String, List<YamlSceneData>> entry : scenesByTitle.entrySet()) {
            if (entry.getValue().size() > 1 && !entry.getKey().isBlank()) {
                issues.add(issue("Structure", "warning", "场景标题重复",
                        "多个 scene 使用相同标题",
                        null, null, null, null,
                        entry.getValue().stream().map(YamlSceneData::title).distinct().toList().toString(),
                        null, "为重复标题追加区分信息"));
            }
        }
        for (Map.Entry<Integer, List<YamlSceneData>> entry : scenesByChapter.entrySet()) {
            if (entry.getKey() > 0 && entry.getValue().size() < 2) {
                issues.add(issue("Structure", "info", "章节场景数过少",
                        "该章节 YAML 场景数少于 2 个",
                        null, entry.getKey(), null, null,
                        String.valueOf(entry.getValue().size()), null, "核对是否遗漏章节内容"));
            }
        }
        for (Integer chapterIndex : context.novelChapterIndexes()) {
            if (!yamlChapterIndexes.contains(chapterIndex)) {
                issues.add(issue("Structure", "error", "有章节完全未被覆盖",
                        "小说章节没有任何 YAML scene 覆盖",
                        null, chapterIndex, null, null,
                        null, null, "补充该章节对应场景"));
            }
        }

        int totalScenes = context.yaml().scenes().size();
        int totalChapters = context.novelChapterIndexes().size();
        int coveredChapters = (int) context.novelChapterIndexes().stream()
                .filter(yamlChapterIndexes::contains)
                .count();
        double sceneHealthScore = totalScenes == 0 ? 100 : healthyScenes * 100.0 / totalScenes;
        double chapterCoverageScore = totalChapters == 0 ? 100 : coveredChapters * 100.0 / totalChapters;
        double score = sceneHealthScore * 0.7 + chapterCoverageScore * 0.3;
        return new EvaluationCheckResult(
                metricWithScore("structure", "结构完整性", score, healthyScenes, totalScenes,
                        "健康场景 %d/%d，章节覆盖 %d/%d，结构问题 %d 个".formatted(
                                healthyScenes, totalScenes, coveredChapters, totalChapters, issues.size())),
                issues
        );
    }

    private Integer firstParagraph(YamlSceneData scene) {
        return scene.sourceRefs().isEmpty() ? null : scene.sourceRefs().get(0).paragraphStart();
    }

    private Integer lastParagraph(YamlSceneData scene) {
        return scene.sourceRefs().isEmpty() ? null : scene.sourceRefs().get(scene.sourceRefs().size() - 1).paragraphEnd();
    }
}
