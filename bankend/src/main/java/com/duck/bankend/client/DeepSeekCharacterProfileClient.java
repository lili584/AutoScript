package com.duck.bankend.client;

import com.duck.bankend.constant.ScriptGenerationConst;
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
public class DeepSeekCharacterProfileClient {

    private static final String SYSTEM_PROMPT = """
            你是小说改编剧本的人物小传整理助手。请基于已生成的 scenes 摘要，为已出现角色生成角色画像。
            必须只输出合法 JSON object，不要输出 Markdown，不要解释。
            顶层 JSON 必须是 {"character_profiles": [...]}。
            每个角色对象必须包含 name、aliases、role、description。
            只能为输入中已出现的角色生成画像，不要新增人物。
            role 用简短中文描述角色定位，例如“主角”“配角-客户”“功能性角色-前台”。
            description 写 2-3 句，基于 scenes 摘要和对白信息概括外貌、性格、身份或背景；不要编造无依据年龄、职业或关系。
            aliases 只列出输入中能推断出的别名、简称、称呼；没有则输出空数组。
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${deepseek.api.base-url}")
    private String baseUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.model}")
    private String model;

    @Value("${deepseek.api.max-tokens:8000}")
    private int maxTokens;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public JsonNode generateProfiles(String sceneSummaryJson) {
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
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "请根据以下 scenes 摘要生成 character_profiles：\n" + sceneSummaryJson)
                ),
                "response_format", Map.of("type", ScriptGenerationConst.RESPONSE_FORMAT_JSON_OBJECT),
                "thinking", Map.of("type", ScriptGenerationConst.THINKING_DISABLED),
                "temperature", ScriptGenerationConst.TEMPERATURE,
                "max_tokens", maxTokens > 0 ? maxTokens : ScriptGenerationConst.DEFAULT_MAX_TOKENS
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
            throw new IllegalStateException("DeepSeek 角色画像请求失败，HTTP " + exception.getStatusCode(), exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException("调用 DeepSeek 角色画像失败: " + exception.getMessage(), exception);
        }
        JsonNode response = readJson(responseBody, "解析 DeepSeek 角色画像响应失败");
        String content = response.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("DeepSeek 角色画像响应缺少 content");
        }
        JsonNode root = readJson(stripCodeFence(content), "解析 DeepSeek 角色画像 JSON 失败");
        JsonNode profiles = root.path("character_profiles");
        return profiles.isArray() ? profiles : objectMapper.createArrayNode();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("构建 DeepSeek 角色画像请求失败", exception);
        }
    }

    private JsonNode readJson(String json, String errorPrefix) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(errorPrefix + ": " + exception.getMessage(), exception);
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }
}
