package com.duck.bankend.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScriptGenerationOverview {

    private ScriptGenerationTaskView latestTask;
    private List<ScriptSceneView> scenes;
}
