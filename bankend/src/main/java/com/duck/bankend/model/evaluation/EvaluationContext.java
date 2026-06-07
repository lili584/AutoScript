package com.duck.bankend.model.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record EvaluationContext(
        NovelEvaluationData novel,
        ScriptYamlData yaml,
        List<YamlDialogueData> yamlDialogues,
        Map<String, YamlCharacterData> charactersById,
        Set<Integer> novelChapterIndexes
) {
}
