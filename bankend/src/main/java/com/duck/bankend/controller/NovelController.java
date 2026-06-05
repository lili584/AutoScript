package com.duck.bankend.controller;

import com.duck.bankend.model.bean.Result;
import com.duck.bankend.model.dto.NovelCreateRequest;
import com.duck.bankend.model.dto.NovelUpdateContentRequest;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.service.NovelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/novels")
public class NovelController {

    private final NovelService novelService;

    @PostMapping
    public Result createNovel(@RequestBody NovelCreateRequest request) {
        try {
            Novel novel = novelService.createNovel(request);
            return Result.success("新建小说成功", novel);
        } catch (IllegalArgumentException exception) {
            return Result.badRequest(exception.getMessage());
        }
    }

    @GetMapping
    public Result listNovels() {
        return Result.searchSuccess(novelService.listActiveNovels());
    }

    @GetMapping("/{id}")
    public Result getNovel(@PathVariable Long id) {
        Novel novel = novelService.getActiveNovel(id);
        if (novel == null) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.searchSuccess(novel);
    }

    @PutMapping("/{id}/content")
    public Result saveContent(@PathVariable Long id, @RequestBody NovelUpdateContentRequest request) {
        Novel novel = novelService.saveContent(id, request == null ? null : request.getContent());
        if (novel == null) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.success("保存小说原始文本成功", novel);
    }

    @DeleteMapping("/{id}")
    public Result deleteNovel(@PathVariable Long id) {
        if (!novelService.softDeleteNovel(id)) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.deleteSuccess();
    }
}
