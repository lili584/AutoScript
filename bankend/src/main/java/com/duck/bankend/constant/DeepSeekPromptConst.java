package com.duck.bankend.constant;

/**
 * DeepSeek 场景抽取 Prompt 常量。
 * <p>
 * 只存放 prompt 文本和 prompt 占位默认值，调用参数仍由 client 负责填充。
 */
public class DeepSeekPromptConst {

    private DeepSeekPromptConst() {
    }

    /**
     * 当 chunk 没有局部上下文或章节滚动摘要时传给模型的占位文本。
     */
    public static final String NONE = "无";

    /**
     * 系统 prompt：约束模型角色、输出 JSON Schema、continuation 标记和 chapter_state。
     */
    public static final String SYSTEM_PROMPT = """
            你是专业的小说改编剧本助手。请把用户提供的小说片段抽取为结构化剧本 scenes。
            必须只输出合法 JSON，不要输出 Markdown，不要解释。
            顶层 JSON 必须是 {"scenes": [...], "chapter_state": {...}}。
            每个 scene 必须包含：scene_id、title、location、time_of_day、summary、characters、beats、source_refs。
            scene_id 必须结合章节和 chunk 保持唯一，建议格式为 chapter-{chapter_index}-chunk-{chunk_index}-scene-{序号}。
            如果当前 chunk 开头延续上一 chunk 的未结束场景，当前 chunk 的第一个 scene 可以设置 is_continuation=true，并填写 continuation_of 和 continuation_reason。
            只有同一章节、同一地点或时间连续、人物和冲突连续时，才允许标记 continuation。
            beats 只能包含 type 为 action、dialogue、transition 的对象。
            dialogue beat 必须包含 character_name 和 text，text 必须保留原文中直接说出口的对白，不要遗漏当前 chunk 内任何直接对白。
            action 和 transition beat 必须包含非空 text；action.text 必须是一句具体可视化的中文动作、环境变化或人物反应，不能为空、不能只写标点。
            对白之间的重要动作、表情和情绪转折必须保留为 action，例如“脚步骤停”“瞳孔骤缩”“捧起灵牌”“掷出残剑”。
            信件、纸条、日记、终端消息、通讯内容如果原文没有明确说出口或朗读，不要改写为 dialogue；请用 action 描述“某某读到/看到……”。
            同一章节内 scene title 应尽量唯一，避免连续输出同名场景。
            source_refs 必须保留用户提供的 chapter_index、chapter_title、chunk_index、paragraph_start、paragraph_end；paragraph_start/end 必须使用当前分块中的 [P数字] 段落编号。
            chapter_state 必须总结处理完当前 chunk 后的章节滚动状态，包含 current_location、active_characters、current_conflict、completed_events、unresolved_questions、open_scene。
            open_scene 表示当前 chunk 结束时仍可能延续到下一个 chunk 的场景；如果没有未结束场景，open_scene.is_resolved=true。
            """;

    /**
     * 用户 prompt 模板：注入小说、章节、chunk、rolling summary、局部 context 和当前分块内容。
     */
    public static final String USER_PROMPT_TEMPLATE = """
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

            质量修正要求：
            %s

            当前分块（每段前的 [P数字] 是原章节内段落编号）：
            %s
            """;

    /**
     * 首次生成时使用的质量提示。
     */
    public static final String DEFAULT_QUALITY_INSTRUCTION = """
            请优先保证 action.text 非空、直接对白完整、叙事内容不要误转对白。
            """;

    /**
     * 质量校验失败后重试时使用的提示模板。
     */
    public static final String RETRY_QUALITY_INSTRUCTION_TEMPLATE = """
            上一次输出未通过后端质量校验：%s。
            请重新抽取当前 chunk：每个 scene 必须包含有效 beats；action/transition text 不得为空；保留所有直接对白；不要把默读的信件、消息或日记转成 dialogue。
            """;
}
