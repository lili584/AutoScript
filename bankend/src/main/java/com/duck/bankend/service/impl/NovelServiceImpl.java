package com.duck.bankend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duck.bankend.mapper.NovelMapper;
import com.duck.bankend.model.dto.NovelCreateRequest;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.service.NovelService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NovelServiceImpl extends ServiceImpl<NovelMapper, Novel> implements NovelService {

    @Override
    public Novel createNovel(NovelCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("小说标题不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        Novel novel = new Novel();
        novel.setTitle(request.getTitle().trim());
        novel.setDescription(trimToNull(request.getDescription()));
        novel.setContent(request.getContent());
        novel.setDeleted(false);
        novel.setCreatedAt(now);
        novel.setUpdatedAt(now);
        save(novel);
        return novel;
    }

    @Override
    public List<Novel> listActiveNovels() {
        return list(new LambdaQueryWrapper<Novel>()
                .eq(Novel::getDeleted, false)
                .orderByDesc(Novel::getCreatedAt));
    }

    @Override
    public Novel getActiveNovel(Long id) {
        if (id == null) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Novel>()
                .eq(Novel::getId, id)
                .eq(Novel::getDeleted, false));
    }

    @Override
    public boolean softDeleteNovel(Long id) {
        Novel novel = getActiveNovel(id);
        if (novel == null) {
            return false;
        }
        novel.setDeleted(true);
        novel.setUpdatedAt(LocalDateTime.now());
        return updateById(novel);
    }

    @Override
    public Novel saveContent(Long id, String content) {
        Novel novel = getActiveNovel(id);
        if (novel == null) {
            return null;
        }
        novel.setContent(content);
        novel.setUpdatedAt(LocalDateTime.now());
        updateById(novel);
        return novel;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
