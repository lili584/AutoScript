package com.duck.bankend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationScorecard {

    private List<EvaluationMetric> metrics;
}
