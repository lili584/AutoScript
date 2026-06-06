package com.duck.bankend.service;

import com.duck.bankend.model.dto.ScriptGenerationTaskView;
import com.duck.bankend.model.entity.ScriptGenerationTask;

public interface ScriptGenerationRuntimeService {

    boolean acquireNovelLock(Long novelId);

    void bindTask(Long novelId, Long taskId);

    void releaseNovelLock(Long novelId);

    void saveTaskState(ScriptGenerationTask task, Integer currentChapterIndex, Integer currentChunkIndex);

    ScriptGenerationTaskView getLatestTask(Long novelId);

    void deleteLatestTask(Long novelId);
}
