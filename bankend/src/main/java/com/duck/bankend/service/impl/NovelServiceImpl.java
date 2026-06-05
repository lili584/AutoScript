package com.duck.bankend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.duck.bankend.mapper.NovelMapper;
import com.duck.bankend.model.dto.NovelAppendContentRequest;
import com.duck.bankend.model.dto.NovelCreateRequest;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.service.NovelService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NovelServiceImpl extends ServiceImpl<NovelMapper, Novel> implements NovelService {

    private static final Pattern ANY_HEADING_PATTERN = Pattern.compile("(?m)^#{1,2}\\s+\\S.*$");
    private static final Pattern TITLE_HEADING_PATTERN = Pattern.compile("(?m)^#\\s+(.+)$");

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

    @Override
    public Novel appendContent(Long id, NovelAppendContentRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("追加内容不能为空");
        }

        Novel novel = getActiveNovel(id);
        if (novel == null) {
            return null;
        }
        novel.setContent(appendWithSeparator(novel.getContent(), request.getContent()));
        novel.setUpdatedAt(LocalDateTime.now());
        updateById(novel);
        return novel;
    }

    @Override
    public Novel createNovelFromMarkdownFile(MultipartFile file, String title, String description) throws IOException {
        MarkdownFile markdownFile = readMarkdownFile(file);
        NovelCreateRequest request = new NovelCreateRequest();
        request.setTitle(resolveTitle(title, markdownFile));
        request.setDescription(description);
        request.setContent(markdownFile.content());
        return createNovel(request);
    }

    @Override
    public Novel saveContentFromMarkdownFile(Long id, MultipartFile file, String mode) throws IOException {
        MarkdownFile markdownFile = readMarkdownFile(file);
        Novel novel = getActiveNovel(id);
        if (novel == null) {
            return null;
        }

        if ("overwrite".equals(mode)) {
            novel.setContent(markdownFile.content());
        } else if ("append".equals(mode)) {
            novel.setContent(appendWithSeparator(novel.getContent(), markdownFile.content()));
        } else {
            throw new IllegalArgumentException("导入模式只能是 overwrite 或 append");
        }

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

    private MarkdownFile readMarkdownFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Markdown 文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException("只支持上传 .md 文件");
        }

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("Markdown 文件内容不能为空");
        }
        if (!ANY_HEADING_PATTERN.matcher(content).find()) {
            throw new IllegalArgumentException("Markdown 文件至少需要包含一个一级或二级标题");
        }

        return new MarkdownFile(filename, content);
    }

    private String resolveTitle(String title, MarkdownFile markdownFile) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }

        Matcher matcher = TITLE_HEADING_PATTERN.matcher(markdownFile.content());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        String filename = markdownFile.filename();
        int dotIndex = filename.toLowerCase(Locale.ROOT).lastIndexOf(".md");
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String appendWithSeparator(String original, String addition) {
        String cleanAddition = addition.trim();
        if (!StringUtils.hasText(original)) {
            return cleanAddition;
        }
        return original.trim() + "\n\n" + cleanAddition;
    }

    private record MarkdownFile(String filename, String content) {
    }
}
