package com.duck.bankend.model.evaluation;

public record YamlBeatData(
        String type,
        String text,
        String characterId,
        String characterName,
        String sceneId,
        int chapterIndex
) {
}
