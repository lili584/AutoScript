package com.duck.bankend.controller;

import com.duck.bankend.model.bean.Result;
import com.duck.bankend.model.dto.NovelAppendContentRequest;
import com.duck.bankend.model.dto.NovelCreateRequest;
import com.duck.bankend.model.dto.NovelUpdateContentRequest;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.service.NovelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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

    @PostMapping(value = "/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result createNovelFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description) {
        try {
            Novel novel = novelService.createNovelFromMarkdownFile(file, title, description);
            return Result.success("上传 Markdown 创建小说成功", novel);
        } catch (IllegalArgumentException exception) {
            return Result.badRequest(exception.getMessage());
        } catch (IOException exception) {
            return Result.error("读取 Markdown 文件失败");
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

    @PostMapping("/{id}/content/append")
    public Result appendContent(@PathVariable Long id, @RequestBody NovelAppendContentRequest request) {
        try {
            Novel novel = novelService.appendContent(id, request);
            if (novel == null) {
                return Result.notFound("小说不存在或已删除");
            }
            return Result.success("追加小说原始文本成功", novel);
        } catch (IllegalArgumentException exception) {
            return Result.badRequest(exception.getMessage());
        }
    }

    @PostMapping(value = "/{id}/content/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result saveContentFromFile(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode) {
        try {
            Novel novel = novelService.saveContentFromMarkdownFile(id, file, mode);
            if (novel == null) {
                return Result.notFound("小说不存在或已删除");
            }
            return Result.success("上传 Markdown 保存小说原始文本成功", novel);
        } catch (IllegalArgumentException exception) {
            return Result.badRequest(exception.getMessage());
        } catch (IOException exception) {
            return Result.error("读取 Markdown 文件失败");
        }
    }

    @DeleteMapping("/{id}")
    public Result deleteNovel(@PathVariable Long id) {
        if (!novelService.softDeleteNovel(id)) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.deleteSuccess();
    }
}
