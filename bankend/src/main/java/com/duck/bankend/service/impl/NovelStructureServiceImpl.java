package com.duck.bankend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duck.bankend.mapper.NovelChapterMapper;
import com.duck.bankend.mapper.NovelChunkMapper;
import com.duck.bankend.model.dto.NovelChapterView;
import com.duck.bankend.model.dto.NovelChunkView;
import com.duck.bankend.model.dto.NovelParseResult;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.entity.NovelChapter;
import com.duck.bankend.model.entity.NovelChunk;
import com.duck.bankend.service.NovelService;
import com.duck.bankend.service.NovelStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NovelStructureServiceImpl implements NovelStructureService {

    private static final int MIN_CHUNK_LENGTH = 1500;
    private static final int MAX_CHUNK_LENGTH = 2000;
    private static final int MIN_CONTEXT_LENGTH = 300;
    private static final int MAX_CONTEXT_LENGTH = 800;
    private static final int MAX_CONTEXT_PARAGRAPHS = 6;
    private static final Pattern CHAPTER_HEADING_PATTERN = Pattern.compile("(?m)^##\\s+(.+)$");

    private final NovelService novelService;
    private final NovelChapterMapper chapterMapper;
    private final NovelChunkMapper chunkMapper;

    @Override
    @Transactional
    public NovelParseResult parseNovel(Long novelId) {
        Novel novel = novelService.getActiveNovel(novelId);
        if (novel == null) {
            return null;
        }
        if (!StringUtils.hasText(novel.getContent())) {
            throw new IllegalArgumentException("小说原始文本不能为空");
        }

        List<ChapterDraft> drafts = parseChapters(novel.getContent());
        chunkMapper.delete(new LambdaQueryWrapper<NovelChunk>().eq(NovelChunk::getNovelId, novelId));
        chapterMapper.delete(new LambdaQueryWrapper<NovelChapter>().eq(NovelChapter::getNovelId, novelId));

        LocalDateTime now = LocalDateTime.now();
        for (ChapterDraft draft : drafts) {
            NovelChapter chapter = new NovelChapter();
            chapter.setNovelId(novelId);
            chapter.setTitle(draft.title());
            chapter.setOrderIndex(draft.orderIndex());
            chapter.setContent(draft.content());
            chapter.setCreatedAt(now);
            chapter.setUpdatedAt(now);
            chapterMapper.insert(chapter);

            List<ChunkDraft> chunks = buildChunks(draft.paragraphs());
            for (ChunkDraft chunkDraft : chunks) {
                NovelChunk chunk = new NovelChunk();
                chunk.setNovelId(novelId);
                chunk.setChapterId(chapter.getId());
                chunk.setChapterIndex(draft.orderIndex());
                chunk.setChunkIndex(chunkDraft.chunkIndex());
                chunk.setContent(chunkDraft.content());
                chunk.setContext(chunkDraft.context());
                chunk.setParagraphStart(chunkDraft.paragraphStart());
                chunk.setParagraphEnd(chunkDraft.paragraphEnd());
                chunk.setCharCount(chunkDraft.content().length());
                chunk.setCreatedAt(now);
                chunk.setUpdatedAt(now);
                chunkMapper.insert(chunk);
            }
        }

        return buildParseResult(novelId);
    }

    @Override
    public List<NovelChapterView> listChapters(Long novelId) {
        return buildChapterViews(novelId);
    }

    @Override
    public List<NovelChunkView> listChunks(Long novelId) {
        return loadChunks(novelId).stream().map(NovelChunkView::from).toList();
    }

    private List<ChapterDraft> parseChapters(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        Matcher matcher = CHAPTER_HEADING_PATTERN.matcher(normalized);
        List<HeadingMatch> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new HeadingMatch(matcher.group(1).trim(), matcher.start(), matcher.end()));
        }
        if (headings.isEmpty()) {
            throw new IllegalArgumentException("未找到章节标题，请使用 ## 标记章节");
        }

        List<ChapterDraft> chapters = new ArrayList<>();
        String preface = stripNovelTitle(normalized.substring(0, headings.get(0).headingStart())).trim();
        int orderIndex = 1;
        if (StringUtils.hasText(preface)) {
            chapters.add(createChapterDraft("正文前言", orderIndex++, preface));
        }

        for (int i = 0; i < headings.size(); i++) {
            HeadingMatch current = headings.get(i);
            int end = i + 1 < headings.size() ? headings.get(i + 1).headingStart() : normalized.length();
            String chapterContent = normalized.substring(current.headingEnd(), end).trim();
            chapters.add(createChapterDraft(current.title(), orderIndex++, chapterContent));
        }
        return chapters;
    }

    private String stripNovelTitle(String content) {
        return content.replaceFirst("(?m)^#\\s+.+(?:\\n|$)", "").trim();
    }

    private ChapterDraft createChapterDraft(String title, int orderIndex, String content) {
        List<String> paragraphs = splitParagraphs(content);
        return new ChapterDraft(title, orderIndex, String.join("\n\n", paragraphs), paragraphs);
    }

    private List<String> splitParagraphs(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String[] parts = content.trim().split("\\n\\s*\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            String paragraph = part.trim();
            if (StringUtils.hasText(paragraph)) {
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    private List<ChunkDraft> buildChunks(List<String> paragraphs) {
        List<ChunkDraft> chunks = new ArrayList<>();
        List<ParagraphRef> current = new ArrayList<>();
        int currentLength = 0;

        for (int i = 0; i < paragraphs.size(); i++) {
            String paragraph = paragraphs.get(i);
            int paragraphLength = paragraph.length();
            int nextLength = current.isEmpty() ? paragraphLength : currentLength + 2 + paragraphLength;

            if (current.isEmpty()) {
                current.add(new ParagraphRef(i + 1, paragraph));
                currentLength = paragraphLength;
                if (paragraphLength > MAX_CHUNK_LENGTH) {
                    addChunk(chunks, current);
                    current = new ArrayList<>();
                    currentLength = 0;
                }
                continue;
            }

            if (nextLength <= MAX_CHUNK_LENGTH) {
                current.add(new ParagraphRef(i + 1, paragraph));
                currentLength = nextLength;
                continue;
            }

            addChunk(chunks, current);
            current = new ArrayList<>();
            current.add(new ParagraphRef(i + 1, paragraph));
            currentLength = paragraphLength;
            if (paragraphLength > MAX_CHUNK_LENGTH) {
                addChunk(chunks, current);
                current = new ArrayList<>();
                currentLength = 0;
            }
        }

        if (!current.isEmpty()) {
            addChunk(chunks, current);
        }

        return chunks;
    }

    private void addChunk(List<ChunkDraft> chunks, List<ParagraphRef> paragraphs) {
        List<ParagraphRef> previousParagraphs = chunks.isEmpty() ? List.of() : chunks.get(chunks.size() - 1).paragraphs();
        String context = buildContext(previousParagraphs);

        String content = paragraphs.stream()
                .map(ParagraphRef::text)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");

        chunks.add(new ChunkDraft(
                chunks.size() + 1,
                content,
                context,
                paragraphs.get(0).index(),
                paragraphs.get(paragraphs.size() - 1).index(),
                List.copyOf(paragraphs)));
    }

    private String buildContext(List<ParagraphRef> previousParagraphs) {
        if (previousParagraphs.isEmpty()) {
            return null;
        }

        List<String> selected = new ArrayList<>();
        int totalLength = 0;
        for (int i = previousParagraphs.size() - 1; i >= 0 && selected.size() < MAX_CONTEXT_PARAGRAPHS; i--) {
            String paragraph = previousParagraphs.get(i).text();
            selected.add(0, paragraph);
            totalLength += paragraph.length();
            if (totalLength >= MIN_CONTEXT_LENGTH) {
                break;
            }
        }

        String context = String.join("\n\n", selected);
        if (context.length() <= MAX_CONTEXT_LENGTH) {
            return context;
        }
        return context.substring(context.length() - MAX_CONTEXT_LENGTH).trim();
    }

    private NovelParseResult buildParseResult(Long novelId) {
        List<NovelChapterView> chapters = buildChapterViews(novelId);
        NovelParseResult result = new NovelParseResult();
        result.setNovelId(novelId);
        result.setChapters(chapters);
        result.setChapterCount(chapters.size());
        result.setChunkCount(chapters.stream().mapToInt(NovelChapterView::getChunkCount).sum());
        return result;
    }

    private List<NovelChapterView> buildChapterViews(Long novelId) {
        List<NovelChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<NovelChapter>()
                .eq(NovelChapter::getNovelId, novelId)
                .orderByAsc(NovelChapter::getOrderIndex));
        List<NovelChunk> chunks = loadChunks(novelId);
        Map<Long, List<NovelChunk>> chunksByChapter = new LinkedHashMap<>();
        for (NovelChunk chunk : chunks) {
            chunksByChapter.computeIfAbsent(chunk.getChapterId(), ignored -> new ArrayList<>()).add(chunk);
        }
        return chapters.stream()
                .map(chapter -> NovelChapterView.from(chapter, chunksByChapter.getOrDefault(chapter.getId(), List.of())))
                .toList();
    }

    private List<NovelChunk> loadChunks(Long novelId) {
        List<NovelChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<NovelChunk>()
                .eq(NovelChunk::getNovelId, novelId)
                .orderByAsc(NovelChunk::getChapterIndex)
                .orderByAsc(NovelChunk::getChunkIndex));
        chunks.sort(Comparator.comparing(NovelChunk::getChapterIndex).thenComparing(NovelChunk::getChunkIndex));
        return chunks;
    }

    private record HeadingMatch(String title, int headingStart, int headingEnd) {
    }

    private record ChapterDraft(String title, int orderIndex, String content, List<String> paragraphs) {
    }

    private record ParagraphRef(int index, String text) {
    }

    private record ChunkDraft(
            int chunkIndex,
            String content,
            String context,
            int paragraphStart,
            int paragraphEnd,
            List<ParagraphRef> paragraphs) {
    }
}
