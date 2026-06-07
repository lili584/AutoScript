package com.duck.bankend.model.evaluation;

public record YamlSourceRefData(
        int chapterIndex,
        String chapterTitle,
        int chunkIndex,
        int paragraphStart,
        int paragraphEnd
) {
}
