package com.duck.bankend.model.dto;

import com.duck.bankend.model.entity.ScriptGenerationTask;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScriptGenerationTaskView {

    private Long id;
    private Long novelId;
    private String status;
    private Integer totalChunks;
    private Integer processedChunks;
    private Integer progressPercent;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScriptGenerationTaskView from(ScriptGenerationTask task) {
        if (task == null) {
            return null;
        }
        ScriptGenerationTaskView view = new ScriptGenerationTaskView();
        view.setId(task.getId());
        view.setNovelId(task.getNovelId());
        view.setStatus(task.getStatus());
        view.setTotalChunks(task.getTotalChunks());
        view.setProcessedChunks(task.getProcessedChunks());
        int total = task.getTotalChunks() == null ? 0 : task.getTotalChunks();
        int processed = task.getProcessedChunks() == null ? 0 : task.getProcessedChunks();
        view.setProgressPercent(total == 0 ? 0 : Math.min(100, processed * 100 / total));
        view.setErrorMessage(task.getErrorMessage());
        view.setStartedAt(task.getStartedAt());
        view.setCompletedAt(task.getCompletedAt());
        view.setCreatedAt(task.getCreatedAt());
        view.setUpdatedAt(task.getUpdatedAt());
        return view;
    }
}
