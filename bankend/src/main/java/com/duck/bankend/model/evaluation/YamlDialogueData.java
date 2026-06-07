package com.duck.bankend.model.evaluation;

public record YamlDialogueData(
        String text,
        String sceneId,
        String characterId,
        String characterName,
        int chapterIndex
) {
}
