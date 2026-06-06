package com.duck.bankend.controller;

import com.duck.bankend.model.bean.Result;
import com.duck.bankend.model.dto.NovelAppendContentRequest;
import com.duck.bankend.model.dto.NovelCreateRequest;
import com.duck.bankend.model.dto.NovelUpdateContentRequest;
import com.duck.bankend.model.dto.ScriptYamlPreview;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.service.NovelService;
import com.duck.bankend.service.NovelStructureService;
import com.duck.bankend.service.ScriptGenerationService;
import com.duck.bankend.service.ScriptYamlExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
    private final NovelStructureService novelStructureService;
    private final ScriptGenerationService scriptGenerationService;
    private final ScriptYamlExportService scriptYamlExportService;

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

    @PostMapping("/{id}/chapters/parse")
    public Result parseChapters(@PathVariable Long id) {
        try {
            Object result = novelStructureService.parseNovel(id);
            if (result == null) {
                return Result.notFound("小说不存在或已删除");
            }
            return Result.success("解析章节成功", result);
        } catch (IllegalArgumentException exception) {
            return Result.badRequest(exception.getMessage());
        }
    }

    @GetMapping("/{id}/chapters")
    public Result listChapters(@PathVariable Long id) {
        if (novelService.getActiveNovel(id) == null) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.searchSuccess(novelStructureService.listChapters(id));
    }

    @GetMapping("/{id}/chunks")
    public Result listChunks(@PathVariable Long id) {
        if (novelService.getActiveNovel(id) == null) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.searchSuccess(novelStructureService.listChunks(id));
    }

    @PostMapping("/{id}/scripts/generate")
    public Result generateScriptScenes(@PathVariable Long id) {
        try {
            Object task = scriptGenerationService.startGeneration(id);
            if (task == null) {
                return Result.notFound("小说不存在或已删除");
            }
            return Result.success("AI 分析任务已创建", task);
        } catch (IllegalArgumentException exception) {
            return Result.badRequest(exception.getMessage());
        }
    }

    @GetMapping("/{id}/scripts/tasks/latest")
    public Result getLatestScriptTask(@PathVariable Long id) {
        if (novelService.getActiveNovel(id) == null) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.searchSuccess(scriptGenerationService.getLatestTask(id));
    }

    @GetMapping("/{id}/scripts/scenes")
    public Result listScriptScenes(@PathVariable Long id) {
        if (novelService.getActiveNovel(id) == null) {
            return Result.notFound("小说不存在或已删除");
        }
        return Result.searchSuccess(scriptGenerationService.listScenes(id));
    }

    @DeleteMapping("/{id}/scripts/scenes")
    public Result clearScriptScenes(@PathVariable Long id) {
        if (novelService.getActiveNovel(id) == null) {
            return Result.notFound("小说不存在或已删除");
        }
        scriptGenerationService.clearScenes(id);
        return Result.deleteSuccess();
    }

    @GetMapping("/{id}/scripts/yaml/preview")
    public Result previewScriptYaml(@PathVariable Long id) {
        try {
            return Result.searchSuccess(scriptYamlExportService.previewYaml(id));
        } catch (IllegalArgumentException exception) {
            return Result.badRequest(exception.getMessage());
        }
    }

    @GetMapping(value = "/{id}/scripts/yaml/download", produces = "text/yaml;charset=UTF-8")
    public ResponseEntity<String> downloadScriptYaml(@PathVariable Long id) {
        try {
            ScriptYamlPreview preview = scriptYamlExportService.downloadYaml(id);
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(preview.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.parseMediaType("text/yaml;charset=UTF-8"))
                    .body(preview.getContent());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(exception.getMessage());
        }
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
