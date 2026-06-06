package com.duck.bankend.model.evaluation;

import java.util.List;

public record ScriptYamlData(
        String schemaVersion,
        String title,
        List<YamlCharacterData> characters,
        List<YamlSceneData> scenes
) {
}
