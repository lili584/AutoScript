package com.duck.bankend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationMetric {

    private String key;
    private String name;
    private Double score;
    private Integer numerator;
    private Integer denominator;
    private String summary;
}
