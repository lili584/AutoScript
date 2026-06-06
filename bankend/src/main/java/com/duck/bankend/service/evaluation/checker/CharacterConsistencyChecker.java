package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.YamlCharacterData;
import com.duck.bankend.model.evaluation.YamlSceneData;
import com.duck.bankend.util.ScriptCharacterNameNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CharacterConsistencyChecker extends BaseEvaluationChecker {

    @Override
    public EvaluationCheckResult check(EvaluationContext context) {
        List<EvaluationIssue> issues = new ArrayList<>();
        Map<String, List<YamlCharacterData>> grouped = new LinkedHashMap<>();
        Set<String> globalIds = new LinkedHashSet<>();
        for (YamlCharacterData character : context.yaml().characters()) {
            if (StringUtils.hasText(character.id())) {
                globalIds.add(character.id());
            }
            String key = ScriptCharacterNameNormalizer.key(character.name());
            if (StringUtils.hasText(key)) {
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(character);
            }
        }

        for (Map.Entry<String, List<YamlCharacterData>> entry : grouped.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            String names = entry.getValue().stream().map(YamlCharacterData::name).filter(StringUtils::hasText).distinct().toList().toString();
            issues.add(issue("Character Consistency", "warning", "角色重复",
                    "多个角色可归并为同一标准名",
                    null, null, null, null,
                    names, null, "建议合并为 " + entry.getKey()));
        }

        for (YamlSceneData scene : context.yaml().scenes()) {
            for (String characterId : scene.characters()) {
                if (StringUtils.hasText(characterId) && !globalIds.contains(characterId)) {
                    issues.add(issue("Character Consistency", "error", "引用了未定义的角色",
                            "scene.characters 引用了 characters 顶层未定义的角色 ID",
                            scene.id(), scene.chapterIndex(), null, null,
                            characterId, null, "在 characters 中补充该角色，或修正引用 ID"));
                }
            }
            scene.beats().stream()
                    .filter(beat -> "dialogue".equals(beat.type()))
                    .filter(beat -> StringUtils.hasText(beat.characterId()) && !globalIds.contains(beat.characterId()))
                    .forEach(beat -> issues.add(issue("Character Consistency", "error", "对白引用了未定义的角色",
                            "dialogue.character_id 未在 characters 顶层定义",
                            scene.id(), scene.chapterIndex(), null, null,
                            beat.characterId(), beat.text(), "修正 character_id 或补充角色定义")));
        }

        int denominator = Math.max(1, context.yaml().characters().size() + context.yaml().scenes().size());
        int numerator = Math.max(0, denominator - issues.size());
        return new EvaluationCheckResult(
                metric("character_consistency", "角色一致性", numerator, denominator, "角色一致性问题 %d 个".formatted(issues.size())),
                issues
        );
    }
}
