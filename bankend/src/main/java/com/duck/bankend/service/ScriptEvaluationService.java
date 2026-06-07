package com.duck.bankend.service;

import com.duck.bankend.model.dto.EvaluationReport;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ScriptEvaluationService {

    EvaluationReport evaluateYaml(Long novelId, MultipartFile file) throws IOException;
}
