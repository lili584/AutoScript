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

        for (YamlSceneData scene : context.yaml().scenes()) {
            scenesByTitle.computeIfAbsent(EvaluationTextUtil.normalize(scene.title()), ignored -> new ArrayList<>()).add(scene);
            scenesByChapter.computeIfAbsent(scene.chapterIndex(), ignored -> new ArrayList<>()).add(scene);
            yamlChapterIndexes.add(scene.chapterIndex());

            if (!scene.id().matches("chapter-\\d+-chunk-\\d+-.+")) {
                issues.add(issue("Structure", "warning", "scene_id 格式异常",
                        "scene_id 不符合 chapter-{n}-chunk-{n}-... 格式",
                        scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                        scene.id(), null, "按生成规范修正 scene_id"));
            }
            if (scene.beats().size() < 3) {
                issues.add(issue("Structure", "warning", "场景 beat 数过少",
                        "场景 beats 少于 3 条，可能缺少动作或对白",
                        scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                        String.valueOf(scene.beats().size()), null, "补充必要 beat"));
            }
            long dialogueCount = scene.beats().stream().filter(beat -> "dialogue".equals(beat.type())).count();
            if (dialogueCount == 0) {
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

        int denominator = Math.max(1, context.yaml().scenes().size() * 4 + context.novelChapterIndexes().size());
        int numerator = Math.max(0, denominator - issues.size());
        return new EvaluationCheckResult(
                metric("structure", "结构完整性", numerator, denominator, "结构问题 %d 个".formatted(issues.size())),
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
