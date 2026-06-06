package com.duck.bankend.service;

import com.duck.bankend.model.dto.ScriptYamlPreview;

public interface ScriptYamlExportService {

    ScriptYamlPreview previewYaml(Long novelId);

    ScriptYamlPreview downloadYaml(Long novelId);
}
