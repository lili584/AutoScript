package com.duck.bankend.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.duck.bankend.constant.ScriptGenerationStatusConst;
import com.duck.bankend.mapper.ScriptGenerationTaskMapper;
import com.duck.bankend.model.entity.ScriptGenerationTask;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 服务启动时恢复 AI 任务最终状态。
 * <p>
 * 后台任务只在当前 JVM 中执行；如果服务中途退出，PG 中遗留的 pending/running 任务不会继续执行，
 * 因此启动时统一标记为 failed，避免前端一直看到运行中。
 */
@Component
@RequiredArgsConstructor
public class ScriptGenerationTaskRecovery {

    private static final String RUNTIME_KEY_PATTERN = "autoscript:script-task:*";

    private final ScriptGenerationTaskMapper taskMapper;
    private final StringRedisTemplate redisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedTasksFailed() {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ScriptGenerationTask> wrapper = new LambdaUpdateWrapper<ScriptGenerationTask>()
                .in(ScriptGenerationTask::getStatus, List.of(ScriptGenerationStatusConst.PENDING, ScriptGenerationStatusConst.RUNNING))
                .set(ScriptGenerationTask::getStatus, ScriptGenerationStatusConst.FAILED)
                .set(ScriptGenerationTask::getErrorMessage, "服务重启或异常退出，AI 分析任务已中断，请重新生成")
                .set(ScriptGenerationTask::getCompletedAt, now)
                .set(ScriptGenerationTask::getUpdatedAt, now);
        taskMapper.update(wrapper);
        clearInterruptedRuntimeState();
    }

    private void clearInterruptedRuntimeState() {
        try {
            Set<String> keys = redisTemplate.keys(RUNTIME_KEY_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RedisConnectionFailureException | RedisSystemException ignored) {
            // Redis 不可用时不阻塞应用启动；PG 已经记录任务中断状态。
        }
    }
}
