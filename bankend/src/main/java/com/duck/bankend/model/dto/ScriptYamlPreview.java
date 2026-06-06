package com.duck.bankend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScriptYamlPreview {

    private String fileName;

    private String content;
}
