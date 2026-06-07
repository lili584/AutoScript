package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;

public interface EvaluationChecker {

    EvaluationCheckResult check(EvaluationContext context);
}
