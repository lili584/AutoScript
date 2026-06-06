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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

    public SceneExtractionResult extractScenes(Novel novel, NovelChapter chapter, NovelChunk chunk, JsonNode previousChapterState) {
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
                        Map.of("role", "user", "content", buildUserPrompt(novel, chapter, chunk, previousChapterState))
                ),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled"),
                "temperature", 0.2,
                "max_tokens", 4000
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(writeJson(request))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(buildHttpErrorMessage(exception), exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException("调用 DeepSeek 失败，请检查网络、base-url 和 API Key 配置: " + exception.getMessage(), exception);
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("DeepSeek 返回内容为空");
        }

        JsonNode response = readJson(responseBody, "解析 DeepSeek 响应失败");
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            String apiMessage = response.path("error").path("message").asText(null);
            throw new IllegalStateException(StringUtils.hasText(apiMessage)
                    ? "DeepSeek 返回错误: " + apiMessage
                    : "DeepSeek 响应缺少 choices[0].message.content");
        }

        JsonNode root = readJson(stripCodeFence(content), "解析 DeepSeek JSON 失败");
        JsonNode scenes = root.path("scenes");
        if (!scenes.isArray()) {
            throw new IllegalStateException("DeepSeek JSON 缺少 scenes 数组");
        }
        JsonNode chapterState = root.path("chapter_state");
        if (!chapterState.isObject()) {
            chapterState = objectMapper.createObjectNode();
        }
        return new SceneExtractionResult(scenes, chapterState);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("构建 DeepSeek 请求 JSON 失败", exception);
        }
    }

    private JsonNode readJson(String json, String errorPrefix) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(errorPrefix + ": " + exception.getMessage(), exception);
        }
    }

    private String buildHttpErrorMessage(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (StringUtils.hasText(responseBody)) {
            try {
                String apiMessage = objectMapper.readTree(responseBody).path("error").path("message").asText(null);
                if (StringUtils.hasText(apiMessage)) {
                    return "DeepSeek 请求失败: " + apiMessage;
                }
            } catch (Exception ignored) {
                return "DeepSeek 请求失败，HTTP " + exception.getStatusCode() + ": " + abbreviate(responseBody);
            }
        }
        return "DeepSeek 请求失败，HTTP " + exception.getStatusCode();
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private String abbreviate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    private String buildSystemPrompt() {
        return """
                你是专业的小说改编剧本助手。请把用户提供的小说片段抽取为结构化剧本 scenes。
                必须只输出合法 JSON，不要输出 Markdown，不要解释。
                顶层 JSON 必须是 {"scenes": [...], "chapter_state": {...}}。
                每个 scene 必须包含：scene_id、title、location、time_of_day、summary、characters、beats、source_refs。
                scene_id 必须结合章节和 chunk 保持唯一，建议格式为 chapter-{chapter_index}-chunk-{chunk_index}-scene-{序号}。
                如果当前 chunk 开头延续上一 chunk 的未结束场景，当前 chunk 的第一个 scene 可以设置 is_continuation=true，并填写 continuation_of 和 continuation_reason。
                只有同一章节、同一地点或时间连续、人物和冲突连续时，才允许标记 continuation。
                beats 只能包含 type 为 action、dialogue、transition 的对象。
                dialogue beat 必须包含 character_name 和 text。
                source_refs 必须保留用户提供的 chapter_index、chapter_title、chunk_index、paragraph_start、paragraph_end。
                chapter_state 必须总结处理完当前 chunk 后的章节滚动状态，包含 current_location、active_characters、current_conflict、completed_events、unresolved_questions、open_scene。
                open_scene 表示当前 chunk 结束时仍可能延续到下一个 chunk 的场景；如果没有未结束场景，open_scene.is_resolved=true。
                """;
    }

    private String buildUserPrompt(Novel novel, NovelChapter chapter, NovelChunk chunk, JsonNode previousChapterState) {
        return """
                请基于以下小说分块抽取剧本 scenes，并输出 JSON。

                小说标题：%s
                章节序号：%d
                章节标题：%s
                chunk 序号：%d
                段落范围：%d-%d

                上一 chunk 后的章节 rolling summary：
                %s

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
                hasChapterState(previousChapterState) ? previousChapterState.toPrettyString() : "无",
                StringUtils.hasText(chunk.getContext()) ? chunk.getContext() : "无",
                chunk.getContent()
        );
    }

    private boolean hasChapterState(JsonNode previousChapterState) {
        return previousChapterState != null && previousChapterState.isObject() && !previousChapterState.isEmpty();
    }

    public record SceneExtractionResult(JsonNode scenes, JsonNode chapterState) {
    }
}
