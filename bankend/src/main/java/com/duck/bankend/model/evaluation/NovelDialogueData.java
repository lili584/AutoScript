package com.duck.bankend.model.evaluation;

public record NovelDialogueData(
        String text,
        int chapterIndex,
        int paragraphNumber,
        String speakerContext
) {
}
