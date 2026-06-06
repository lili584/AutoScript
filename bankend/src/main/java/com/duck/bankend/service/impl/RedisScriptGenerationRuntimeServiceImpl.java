package com.duck.bankend.service.impl;

import com.duck.bankend.conf.ScriptGenerationRuntimeProperties;
import com.duck.bankend.model.dto.ScriptGenerationTaskView;
import com.duck.bankend.model.entity.ScriptGenerationTask;
import com.duck.bankend.service.ScriptGenerationRuntimeService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisScriptGenerationRuntimeServiceImpl implements ScriptGenerationRuntimeService {

    private static final String KEY_PREFIX = "autoscript:script-task:";

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final ScriptGenerationRuntimeProperties properties;

    @Override
    public boolean acquireNovelLock(Long novelId) {
        try {
            RLock lock = redissonClient.getLock(lockKey(novelId));
            return lock.tryLock();
        } catch (RedisConnectionFailureException | RedisSystemException | RedisException exception) {
            throw new IllegalArgumentException("Redis 不可用，无法启动 AI 分析任务，请先启动 Redis");
        }
    }

    @Override
    public void bindTask(Long novelId, Long taskId) {
        try {
            redisTemplate.opsForValue().set(latestKey(novelId), String.valueOf(taskId), ttl());
        } catch (RedisConnectionFailureException | RedisSystemException exception) {
            throw new IllegalArgumentException("Redis 不可用，无法写入 AI 任务运行态");
        }
    }

    @Override
    public void releaseNovelLock(Long novelId) {
        try {
            redissonClient.getLock(lockKey(novelId)).forceUnlock();
        } catch (RedisConnectionFailureException | RedisSystemException | RedisException ignored) {
            // 释放锁失败不影响 PG 中的最终任务状态。
        }
    }

    @Override
    public void saveTaskState(ScriptGenerationTask task, Integer currentChapterIndex, Integer currentChunkIndex) {
        if (task == null || task.getId() == null || task.getNovelId() == null) {
            return;
        }
        try {
            Map<String, String> values = new HashMap<>();
            put(values, "id", task.getId());
            put(values, "novelId", task.getNovelId());
            put(values, "status", task.getStatus());
            put(values, "totalChunks", task.getTotalChunks());
            put(values, "processedChunks", task.getProcessedChunks());
            put(values, "errorMessage", task.getErrorMessage());
            put(values, "startedAt", task.getStartedAt());
            put(values, "completedAt", task.getCompletedAt());
            put(values, "createdAt", task.getCreatedAt());
            put(values, "updatedAt", task.getUpdatedAt());
            put(values, "currentChapterIndex", currentChapterIndex);
            put(values, "currentChunkIndex", currentChunkIndex);

            String runtimeKey = runtimeKey(task.getId());
            redisTemplate.opsForHash().putAll(runtimeKey, values);
            redisTemplate.expire(runtimeKey, ttl());
            redisTemplate.opsForValue().set(latestKey(task.getNovelId()), String.valueOf(task.getId()), ttl());
        } catch (RedisConnectionFailureException | RedisSystemException ignored) {
            // 运行态缓存失败时继续执行任务，最终状态仍会写入 PG。
        }
    }

    @Override
    public ScriptGenerationTaskView getLatestTask(Long novelId) {
        try {
            String taskId = redisTemplate.opsForValue().get(latestKey(novelId));
            if (!StringUtils.hasText(taskId)) {
                return null;
            }
            Map<Object, Object> values = redisTemplate.opsForHash().entries(runtimeKey(Long.valueOf(taskId)));
            if (values.isEmpty()) {
                return null;
            }
            return toTaskView(values);
        } catch (RedisConnectionFailureException | RedisSystemException | NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public void deleteLatestTask(Long novelId) {
        try {
            String taskId = redisTemplate.opsForValue().get(latestKey(novelId));
            if (StringUtils.hasText(taskId)) {
                redisTemplate.delete(runtimeKey(Long.valueOf(taskId)));
            }
            redisTemplate.delete(latestKey(novelId));
            releaseNovelLock(novelId);
        } catch (RedisConnectionFailureException | RedisSystemException | NumberFormatException ignored) {
            // 清理运行态失败不影响数据库清理。
        }
    }

    private ScriptGenerationTaskView toTaskView(Map<Object, Object> values) {
        ScriptGenerationTask task = new ScriptGenerationTask();
        task.setId(longValue(values.get("id")));
        task.setNovelId(longValue(values.get("novelId")));
        task.setStatus(stringValue(values.get("status")));
        task.setTotalChunks(intValue(values.get("totalChunks")));
        task.setProcessedChunks(intValue(values.get("processedChunks")));
        task.setErrorMessage(stringValue(values.get("errorMessage")));
        task.setStartedAt(timeValue(values.get("startedAt")));
        task.setCompletedAt(timeValue(values.get("completedAt")));
        task.setCreatedAt(timeValue(values.get("createdAt")));
        task.setUpdatedAt(timeValue(values.get("updatedAt")));
        return ScriptGenerationTaskView.from(task);
    }

    private Duration ttl() {
        return Duration.ofMinutes(Math.max(1, properties.getRuntimeTtlMinutes()));
    }

    private void put(Map<String, String> values, String key, Object value) {
        if (value != null) {
            values.put(key, String.valueOf(value));
        }
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private LocalDateTime timeValue(Object value) {
        return value == null ? null : LocalDateTime.parse(String.valueOf(value));
    }

    private String stringValue(Object value) {
        String text = value == null ? null : String.valueOf(value);
        return StringUtils.hasText(text) ? text : null;
    }

    private String latestKey(Long novelId) {
        return KEY_PREFIX + "latest:" + novelId;
    }

    private String lockKey(Long novelId) {
        return KEY_PREFIX + "lock:" + novelId;
    }

    private String runtimeKey(Long taskId) {
        return KEY_PREFIX + "runtime:" + taskId;
    }
}
