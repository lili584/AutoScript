package com.duck.bankend.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class NovelParseResult {

    private Long novelId;
    private Integer chapterCount;
    private Integer chunkCount;
    private List<NovelChapterView> chapters;
}
