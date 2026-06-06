package com.duck.bankend.constant;

/**
 * AI 剧本场景生成流程常量。
 * <p>
 * 用于 DeepSeek 调用、scene 去重、跨 chunk 合并和错误信息截断。
 */
public class ScriptGenerationConst {

    private ScriptGenerationConst() {
    }

    /**
     * DeepSeek OpenAI 兼容 Chat Completions 路径。
     */
    public static final String CHAT_COMPLETIONS_URI = "/chat/completions";

    /**
     * DeepSeek JSON 输出格式类型。
     */
    public static final String RESPONSE_FORMAT_JSON_OBJECT = "json_object";

    /**
     * DeepSeek 推理关闭配置值，当前模型请求中显式关闭 thinking。
     */
    public static final String THINKING_DISABLED = "disabled";

    /**
     * 场景抽取温度。偏低以减少格式漂移和过度发挥。
     */
    public static final double TEMPERATURE = 0.2;

    /**
     * 单个 chunk 场景抽取最大输出 token 数。
     */
    public static final int MAX_TOKENS = 4000;

    /**
     * HTTP 错误响应在任务错误信息中的最大展示长度。
     */
    public static final int RESPONSE_ABBREVIATE_LENGTH = 300;

    /**
     * 同章节 summary 简单文本相似度阈值，达到该值则合并到已有 scene。
     */
    public static final double SUMMARY_SIMILARITY_THRESHOLD = 0.9;

    /**
     * 已包含章节和 chunk 前缀的 scene_id 识别表达式。
     */
    public static final String SCENE_ID_WITH_SOURCE_REGEX = "chapter-\\d+-chunk-\\d+-.+";

    /**
     * 后端补全 scene_id 时使用的默认局部编号模板。
     */
    public static final String DEFAULT_SCENE_ID_TEMPLATE = "scene-%d";

    /**
     * 后端统一生成带章节和 chunk 来源的 scene_id 模板。
     */
    public static final String SCENE_ID_WITH_SOURCE_TEMPLATE = "chapter-%d-chunk-%d-%s";
}
