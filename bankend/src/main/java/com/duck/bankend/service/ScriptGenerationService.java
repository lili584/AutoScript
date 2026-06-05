package com.duck.bankend.service;

import com.duck.bankend.model.dto.ScriptGenerationOverview;
import com.duck.bankend.model.dto.ScriptGenerationTaskView;
import com.duck.bankend.model.dto.ScriptSceneView;

import java.util.List;

public interface ScriptGenerationService {

    ScriptGenerationTaskView startGeneration(Long novelId);

    ScriptGenerationTaskView getLatestTask(Long novelId);

    List<ScriptSceneView> listScenes(Long novelId);

    void clearScenes(Long novelId);

    ScriptGenerationOverview getOverview(Long novelId);
}
