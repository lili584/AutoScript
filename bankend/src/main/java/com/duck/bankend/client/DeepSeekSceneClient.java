package com.duck.bankend.client;

import com.duck.bankend.constant.DeepSeekPromptConst;
import com.duck.bankend.constant.ScriptGenerationConst;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.entity.NovelChapter;
import com.duck.bankend.model.entity.NovelChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
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
                        Map.of("role", "system", "content", DeepSeekPromptConst.SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserPrompt(novel, chapter, chunk, previousChapterState))
                ),
                "response_format", Map.of("type", ScriptGenerationConst.RESPONSE_FORMAT_JSON_OBJECT),
                "thinking", Map.of("type", ScriptGenerationConst.THINKING_DISABLED),
                "temperature", ScriptGenerationConst.TEMPERATURE,
                "max_tokens", ScriptGenerationConst.MAX_TOKENS
        );

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(ScriptGenerationConst.CHAT_COMPLETIONS_URI)
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
        return value.length() <= ScriptGenerationConst.RESPONSE_ABBREVIATE_LENGTH
                ? value
                : value.substring(0, ScriptGenerationConst.RESPONSE_ABBREVIATE_LENGTH) + "...";
    }

    private String buildUserPrompt(Novel novel, NovelChapter chapter, NovelChunk chunk, JsonNode previousChapterState) {
        return DeepSeekPromptConst.USER_PROMPT_TEMPLATE.formatted(
                novel.getTitle(),
                chunk.getChapterIndex(),
                chapter.getTitle(),
                chunk.getChunkIndex(),
                chunk.getParagraphStart(),
                chunk.getParagraphEnd(),
                hasChapterState(previousChapterState) ? previousChapterState.toPrettyString() : DeepSeekPromptConst.NONE,
                StringUtils.hasText(chunk.getContext()) ? chunk.getContext() : DeepSeekPromptConst.NONE,
                chunk.getContent()
        );
    }

    private boolean hasChapterState(JsonNode previousChapterState) {
        return previousChapterState != null && previousChapterState.isObject() && !previousChapterState.isEmpty();
    }

    public record SceneExtractionResult(JsonNode scenes, JsonNode chapterState) {
    }
}
