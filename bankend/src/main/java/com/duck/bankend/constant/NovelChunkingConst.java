package com.duck.bankend.constant;

/**
 * 小说 Markdown 解析和 chunk 分片常量。
 * <p>
 * 仅用于小说原始文本切章、切块、构建局部上下文。
 */
public class NovelChunkingConst {

    private NovelChunkingConst() {
    }

    /**
     * chunk 目标下限。当前逻辑以段落为最小单位，尽量让 chunk 接近该长度。
     */
    public static final int MIN_CHUNK_LENGTH = 1500;

    /**
     * chunk 目标上限。单段超过该长度时不强切，允许单段独立成为 chunk。
     */
    public static final int MAX_CHUNK_LENGTH = 2000;

    /**
     * 局部 context 期望最小字数，用于短段落密集文本时补足上下文。
     */
    public static final int MIN_CONTEXT_LENGTH = 300;

    /**
     * 局部 context 最大字数，避免 AI 输入无限增长。
     */
    public static final int MAX_CONTEXT_LENGTH = 800;

    /**
     * 局部 context 最多回看前一个 chunk 的段落数。
     */
    public static final int MAX_CONTEXT_PARAGRAPHS = 6;

    /**
     * Markdown 二级标题匹配表达式，## 表示章节标题。
     */
    public static final String CHAPTER_HEADING_REGEX = "(?m)^##\\s+(.+)$";
}
