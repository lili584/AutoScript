package com.duck.bankend.model.evaluation;

import java.util.List;

public record NovelEvaluationData(
        Long novelId,
        String title,
        String fullText,
        List<NovelChapterData> chapters,
        List<NovelParagraphData> paragraphs,
        List<NovelDialogueData> dialogues
) {
}
