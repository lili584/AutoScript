package com.duck.bankend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.duck.bankend.model.dto.NovelCreateRequest;
import com.duck.bankend.model.entity.Novel;

import java.util.List;

public interface NovelService extends IService<Novel> {

    Novel createNovel(NovelCreateRequest request);

    List<Novel> listActiveNovels();

    Novel getActiveNovel(Long id);

    boolean softDeleteNovel(Long id);

    Novel saveContent(Long id, String content);
}
