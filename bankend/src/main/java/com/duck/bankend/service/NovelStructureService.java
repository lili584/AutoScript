package com.duck.bankend.service;

import com.duck.bankend.model.dto.NovelChapterView;
import com.duck.bankend.model.dto.NovelChunkView;
import com.duck.bankend.model.dto.NovelParseResult;

import java.util.List;

public interface NovelStructureService {

    NovelParseResult parseNovel(Long novelId);

    List<NovelChapterView> listChapters(Long novelId);

    List<NovelChunkView> listChunks(Long novelId);
}
