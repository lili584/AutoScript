package com.duck.bankend.model.dto;

import com.duck.bankend.model.entity.NovelChunk;
import lombok.Data;

@Data
public class NovelChunkView {

    private Long id;
    private Integer chapterIndex;
    private Integer chunkIndex;
    private Integer paragraphStart;
    private Integer paragraphEnd;
    private Integer charCount;
    private Boolean hasContext;
    private String content;
    private String context;

    public static NovelChunkView from(NovelChunk chunk) {
        NovelChunkView view = new NovelChunkView();
        view.setId(chunk.getId());
        view.setChapterIndex(chunk.getChapterIndex());
        view.setChunkIndex(chunk.getChunkIndex());
        view.setParagraphStart(chunk.getParagraphStart());
        view.setParagraphEnd(chunk.getParagraphEnd());
        view.setCharCount(chunk.getCharCount());
        view.setHasContext(chunk.getContext() != null && !chunk.getContext().isBlank());
        view.setContent(chunk.getContent());
        view.setContext(chunk.getContext());
        return view;
    }
}
