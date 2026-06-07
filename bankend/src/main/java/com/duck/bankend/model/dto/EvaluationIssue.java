package com.duck.bankend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationIssue {

    private String checker;
    private String severity;
    private String type;
    private String message;
    private String sceneId;
    private Integer chapterIndex;
    private Integer paragraphStart;
    private Integer paragraphEnd;
    private String yamlText;
    private String novelText;
    private String suggestion;
}
