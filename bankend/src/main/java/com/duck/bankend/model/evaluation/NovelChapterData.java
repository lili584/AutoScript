package com.duck.bankend.model.evaluation;

import java.util.List;

public record NovelChapterData(
        int index,
        String title,
        List<NovelParagraphData> paragraphs
) {
}
