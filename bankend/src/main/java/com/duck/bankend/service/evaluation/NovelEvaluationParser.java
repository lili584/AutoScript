package com.duck.bankend.service.evaluation;

import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.evaluation.NovelChapterData;
import com.duck.bankend.model.evaluation.NovelDialogueData;
import com.duck.bankend.model.evaluation.NovelEvaluationData;
import com.duck.bankend.model.evaluation.NovelParagraphData;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NovelEvaluationParser {

    private static final Pattern CHAPTER_TITLE_PATTERN = Pattern.compile("^##\\s+(.+)$");
    private static final Pattern NOVEL_TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$");
    private static final Pattern DIRECT_QUOTE_PATTERN = Pattern.compile("[“\"「『‘]([^”\"」』’]+)[”\"」』’]");

    public NovelEvaluationData parse(Novel novel) {
        if (novel == null || !StringUtils.hasText(novel.getContent())) {
            throw new IllegalArgumentException("小说原始文本为空，无法测评");
        }
        String content = normalizeNewlines(novel.getContent());
        List<NovelChapterData> chapters = parseChapters(content);
        List<NovelParagraphData> paragraphs = chapters.stream()
                .flatMap(chapter -> chapter.paragraphs().stream())
                .toList();
        List<NovelDialogueData> dialogues = extractDialogues(paragraphs);
        return new NovelEvaluationData(novel.getId(), novel.getTitle(), content, chapters, paragraphs, dialogues);
    }

    private List<NovelChapterData> parseChapters(String content) {
        List<NovelChapterData> chapters = new ArrayList<>();
        String currentTitle = "正文";
        List<String> chapterLines = new ArrayList<>();
        int chapterIndex = 1;
        boolean hasChapterTitle = false;

        for (String line : content.split("\\n")) {
            Matcher chapterMatcher = CHAPTER_TITLE_PATTERN.matcher(line);
            if (chapterMatcher.matches()) {
                if (!chapterLines.isEmpty() || hasChapterTitle) {
                    chapters.add(buildChapter(chapterIndex++, currentTitle, chapterLines));
                    chapterLines = new ArrayList<>();
                }
                hasChapterTitle = true;
                currentTitle = chapterMatcher.group(1).trim();
                continue;
            }
            if (NOVEL_TITLE_PATTERN.matcher(line).matches()) {
                continue;
            }
            chapterLines.add(line);
        }
        if (!chapterLines.isEmpty() || chapters.isEmpty()) {
            chapters.add(buildChapter(chapterIndex, currentTitle, chapterLines));
        }
        return chapters;
    }

    private NovelChapterData buildChapter(int chapterIndex, String title, List<String> lines) {
        String body = String.join("\n", lines).trim();
        List<NovelParagraphData> paragraphs = new ArrayList<>();
        int paragraphNumber = 1;
        if (StringUtils.hasText(body)) {
            for (String paragraph : body.split("\\n\\s*\\n+")) {
                if (StringUtils.hasText(paragraph)) {
                    paragraphs.add(new NovelParagraphData(chapterIndex, paragraphNumber++, paragraph.trim()));
                }
            }
        }
        return new NovelChapterData(chapterIndex, title, paragraphs);
    }

    private List<NovelDialogueData> extractDialogues(List<NovelParagraphData> paragraphs) {
        List<NovelDialogueData> dialogues = new ArrayList<>();
        for (NovelParagraphData paragraph : paragraphs) {
            Matcher matcher = DIRECT_QUOTE_PATTERN.matcher(paragraph.text());
            while (matcher.find()) {
                String text = matcher.group(1);
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                int contextStart = Math.max(0, matcher.start() - 15);
                String speakerContext = paragraph.text().substring(contextStart, matcher.start()).trim();
                dialogues.add(new NovelDialogueData(text.trim(), paragraph.chapterIndex(), paragraph.paragraphNumber(), speakerContext));
            }
        }
        return dialogues;
    }

    private String normalizeNewlines(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }
}
