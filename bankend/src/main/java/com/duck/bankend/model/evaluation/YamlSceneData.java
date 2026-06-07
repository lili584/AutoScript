package com.duck.bankend.model.evaluation;

import java.util.List;

public record YamlSceneData(
        String id,
        int order,
        int chapterIndex,
        String chapterTitle,
        String title,
        String location,
        String timeOfDay,
        String summary,
        List<String> characters,
        List<YamlBeatData> beats,
        List<YamlSourceRefData> sourceRefs
) {
}
