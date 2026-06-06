package com.duck.bankend.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 剧本生成运行时配置。
 * <p>
 * 只控制后台任务并发、DeepSeek 请求并发和 Redis 运行态 TTL，不影响 AI prompt 或数据库结构。
 */
@Data
@ConfigurationProperties(prefix = "script.generation")
public class ScriptGenerationRuntimeProperties {

    /**
     * 同一 JVM 内最多同时运行的 AI 分析任务数量。
     */
    private int maxRunningTasks = 1;

    /**
     * 同一 JVM 内最多同时发出的 DeepSeek 请求数量。
     */
    private int maxDeepseekRequests = 1;

    /**
     * Redis 中任务运行态和运行锁的 TTL，单位分钟。
     */
    private int runtimeTtlMinutes = 30;
}
