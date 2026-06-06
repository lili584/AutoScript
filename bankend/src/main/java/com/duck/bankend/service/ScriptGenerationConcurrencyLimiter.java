package com.duck.bankend.service;

import com.duck.bankend.conf.ScriptGenerationRuntimeProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * AI 剧本生成并发控制。
 * <p>
 * 任务级令牌用于控制同时运行的小说分析任务；请求级令牌用于控制同时发出的 DeepSeek 请求。
 */
@Component
public class ScriptGenerationConcurrencyLimiter {

    private final Semaphore taskSemaphore;
    private final Semaphore deepSeekRequestSemaphore;

    public ScriptGenerationConcurrencyLimiter(ScriptGenerationRuntimeProperties properties) {
        this.taskSemaphore = new Semaphore(Math.max(1, properties.getMaxRunningTasks()));
        this.deepSeekRequestSemaphore = new Semaphore(Math.max(1, properties.getMaxDeepseekRequests()));
    }

    public boolean tryAcquireTask() {
        return taskSemaphore.tryAcquire();
    }

    public void releaseTask() {
        taskSemaphore.release();
    }

    public void acquireDeepSeekRequest() {
        try {
            deepSeekRequestSemaphore.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 DeepSeek 请求令牌时被中断", exception);
        }
    }

    public void releaseDeepSeekRequest() {
        deepSeekRequestSemaphore.release();
    }
}
