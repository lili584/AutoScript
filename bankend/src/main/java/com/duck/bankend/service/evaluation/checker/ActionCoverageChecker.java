package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.YamlBeatData;
import com.duck.bankend.model.evaluation.YamlSceneData;
import com.duck.bankend.util.EvaluationTextUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ActionCoverageChecker extends BaseEvaluationChecker {

    @Override
    public EvaluationCheckResult check(EvaluationContext context) {
        List<EvaluationIssue> issues = new ArrayList<>();
        int total = 0;
        int valid = 0;
        for (YamlSceneData scene : context.yaml().scenes()) {
            int sceneTotal = 0;
            int sceneValid = 0;
            for (YamlBeatData beat : scene.beats()) {
                if (!isActionLike(beat.type())) {
                    continue;
                }
                total++;
                sceneTotal++;
                if (EvaluationTextUtil.hasMeaningfulText(beat.text())) {
                    valid++;
                    sceneValid++;
                } else {
                    issues.add(issue("Action Coverage", "warning", "空动作描写",
                            "action/transition beat 缺少有效文本",
                            scene.id(), scene.chapterIndex(), null, null,
                            beat.text(), null, "补充具体动作、环境变化或人物反应"));
                }
            }
            if (sceneTotal == 0) {
                issues.add(issue("Action Coverage", "warning", "场景缺少动作描写",
                        "场景中没有 action/transition beat",
                        scene.id(), scene.chapterIndex(), null, null,
                        null, null, "至少补充一条动作或环境描写"));
            } else if (sceneValid == 0) {
                issues.add(issue("Action Coverage", "error", "场景完全无动作描写",
                        "场景 action/transition 全部为空或无意义",
                        scene.id(), scene.chapterIndex(), null, null,
                        null, null, "补充有效动作描写"));
            }
        }
        return new EvaluationCheckResult(
                metric("action_coverage", "动作覆盖率", valid, total, "有效动作描写 %d/%d".formatted(valid, total)),
                issues
        );
    }

    private boolean isActionLike(String type) {
        return "action".equals(type) || "transition".equals(type);
    }
}
