package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.NovelChapterData;
import com.duck.bankend.model.evaluation.YamlBeatData;
import com.duck.bankend.model.evaluation.YamlCharacterData;
import com.duck.bankend.model.evaluation.YamlSceneData;
import com.duck.bankend.util.EvaluationTextUtil;
import com.duck.bankend.util.ScriptCharacterNameNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class FidelityChecker extends BaseEvaluationChecker {

    @Override
    public EvaluationCheckResult check(EvaluationContext context) {
        List<EvaluationIssue> issues = new ArrayList<>();
        Map<Integer, String> chapterTextByIndex = context.novel().chapters().stream()
                .collect(Collectors.toMap(NovelChapterData::index, chapter -> chapter.paragraphs().stream()
                        .map(paragraph -> paragraph.text())
                        .collect(Collectors.joining("\n")), (left, right) -> left));

        int checks = 0;
        for (YamlSceneData scene : context.yaml().scenes()) {
            String chapterText = chapterTextByIndex.getOrDefault(scene.chapterIndex(), context.novel().fullText());
            if (StringUtils.hasText(scene.location())) {
                checks++;
                if (!EvaluationTextUtil.containsNormalized(chapterText, scene.location())) {
                    issues.add(issue("Fidelity", "warning", "场景地点原文无依据",
                            "location 在对应章节原文中没有找到明确依据",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            scene.location(), null, "核对地点是否为 AI 概括或幻觉"));
                }
            }
            if (StringUtils.hasText(scene.timeOfDay())) {
                checks++;
                if (!EvaluationTextUtil.containsNormalized(chapterText, scene.timeOfDay())) {
                    issues.add(issue("Fidelity", "info", "场景时间原文依据较弱",
                            "time_of_day 在对应章节原文中没有找到直接文本",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            scene.timeOfDay(), null, "如为合理概括可忽略，否则修正时间"));
                }
            }

            Set<String> sceneCharacterNames = sceneCharacterNames(scene, context.charactersById());
            for (YamlBeatData beat : scene.beats()) {
                if (!"dialogue".equals(beat.type())) {
                    continue;
                }
                checks++;
                String speakerKey = ScriptCharacterNameNormalizer.key(beat.characterName());
                if (StringUtils.hasText(speakerKey) && !sceneCharacterNames.contains(speakerKey)) {
                    issues.add(issue("Fidelity", "warning", "对白角色未在场景人物列表中",
                            "dialogue.character_name 未出现在 scene.characters 引用的人物中",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            beat.characterName(), beat.text(), "补充 scene.characters 或修正对白角色"));
                }
                if (EvaluationTextUtil.containsNormalized(context.novel().fullText(), beat.text())
                        && !EvaluationTextUtil.containsAsDirectQuote(context.novel().fullText(), beat.text())) {
                    issues.add(issue("Fidelity", "error", "叙事内容被转为对白",
                            "dialogue.text 在原文中存在，但不是引号包裹的直接对白",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            beat.text(), null, "改为 action 或删除该 dialogue"));
                }
            }
        }
        int numerator = Math.max(0, checks - issues.size());
        return new EvaluationCheckResult(
                metric("fidelity", "忠实度", numerator, checks, "忠实度问题 %d 个".formatted(issues.size())),
                issues
        );
    }

    private Set<String> sceneCharacterNames(YamlSceneData scene, Map<String, YamlCharacterData> charactersById) {
        Set<String> names = new LinkedHashSet<>();
        for (String id : scene.characters()) {
            YamlCharacterData character = charactersById.get(id);
            if (character != null) {
                names.add(ScriptCharacterNameNormalizer.key(character.name()));
            }
        }
        return names;
    }

    private Integer firstParagraph(YamlSceneData scene) {
        return scene.sourceRefs().isEmpty() ? null : scene.sourceRefs().get(0).paragraphStart();
    }

    private Integer lastParagraph(YamlSceneData scene) {
        return scene.sourceRefs().isEmpty() ? null : scene.sourceRefs().get(scene.sourceRefs().size() - 1).paragraphEnd();
    }
}
