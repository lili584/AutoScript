package com.duck.bankend.model.dto;

import com.duck.bankend.model.entity.NovelChapter;
import com.duck.bankend.model.entity.NovelChunk;
import lombok.Data;

import java.util.List;

@Data
public class NovelChapterView {

    private Long id;
    private String title;
    private Integer orderIndex;
    private Integer chunkCount;
    private String content;
    private List<NovelChunkView> chunks;

    public static NovelChapterView from(NovelChapter chapter, List<NovelChunk> chunks) {
        NovelChapterView view = new NovelChapterView();
        view.setId(chapter.getId());
        view.setTitle(chapter.getTitle());
        view.setOrderIndex(chapter.getOrderIndex());
        view.setContent(chapter.getContent());
        view.setChunkCount(chunks.size());
        view.setChunks(chunks.stream().map(NovelChunkView::from).toList());
        return view;
    }
}
