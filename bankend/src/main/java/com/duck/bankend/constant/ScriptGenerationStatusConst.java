package com.duck.bankend.constant;

/**
 * 剧本生成任务状态常量。
 * <p>
 * 仅用于 script_generation_tasks.status，避免状态字符串散落在服务实现中。
 */
public class ScriptGenerationStatusConst {

    private ScriptGenerationStatusConst() {
    }

    /**
     * 任务已创建，等待后台线程处理。
     */
    public static final String PENDING = "pending";

    /**
     * 任务正在调用 AI 并保存 scene 草稿。
     */
    public static final String RUNNING = "running";

    /**
     * 所有 chunk 已处理完成。
     */
    public static final String SUCCEEDED = "succeeded";

    /**
     * 任务处理失败，失败原因写入 error_message。
     */
    public static final String FAILED = "failed";
}
