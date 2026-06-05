package com.duck.bankend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.entity.NovelChapter;
import com.duck.bankend.model.entity.NovelChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class DeepSeekSceneClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${deepseek.api.base-url}")
    private String baseUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.model}")
    private String model;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public JsonNode extractScenes(Novel novel, NovelChapter chapter, NovelChunk chunk) {
        if (!isConfigured()) {
            throw new IllegalStateException("未配置 DEEPSEEK_API_KEY");
        }

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt()),
                        Map.of("role", "user", "content", buildUserPrompt(novel, chapter, chunk))
                ),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled"),
                "temperature", 0.2,
                "max_tokens", 4000
        );

        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        String content = response == null
                ? null
                : response.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("DeepSeek 返回内容为空");
        }

        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode scenes = root.path("scenes");
            if (!scenes.isArray()) {
                throw new IllegalStateException("DeepSeek JSON 缺少 scenes 数组");
            }
            return scenes;
        } catch (Exception exception) {
            throw new IllegalStateException("解析 DeepSeek JSON 失败: " + exception.getMessage(), exception);
        }
    }

    private String buildSystemPrompt() {
        return """
                你是专业的小说改编剧本助手。请把用户提供的小说片段抽取为结构化剧本 scenes。
                必须只输出合法 JSON，不要输出 Markdown，不要解释。
                顶层 JSON 必须是 {"scenes": [...]}。
                每个 scene 必须包含：scene_id、title、location、time_of_day、summary、characters、beats、source_refs。
                beats 只能包含 type 为 action、dialogue、transition 的对象。
                dialogue beat 必须包含 character_name 和 text。
                source_refs 必须保留用户提供的 chapter_index、chapter_title、chunk_index、paragraph_start、paragraph_end。
                """;
    }

    private String buildUserPrompt(Novel novel, NovelChapter chapter, NovelChunk chunk) {
        return """
                请基于以下小说分块抽取剧本 scenes，并输出 JSON。

                小说标题：%s
                章节序号：%d
                章节标题：%s
                chunk 序号：%d
                段落范围：%d-%d

                上下文：
                %s

                当前分块：
                %s
                """.formatted(
                novel.getTitle(),
                chunk.getChapterIndex(),
                chapter.getTitle(),
                chunk.getChunkIndex(),
                chunk.getParagraphStart(),
                chunk.getParagraphEnd(),
                StringUtils.hasText(chunk.getContext()) ? chunk.getContext() : "无",
                chunk.getContent()
        );
    }
}
