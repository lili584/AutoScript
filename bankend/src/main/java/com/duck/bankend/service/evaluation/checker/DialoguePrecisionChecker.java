package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.NovelDialogueData;
import com.duck.bankend.model.evaluation.YamlDialogueData;
import com.duck.bankend.util.EvaluationTextUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DialoguePrecisionChecker extends BaseEvaluationChecker {

    @Override
    public EvaluationCheckResult check(EvaluationContext context) {
        List<EvaluationIssue> issues = new ArrayList<>();
        int accepted = 0;
        for (YamlDialogueData yamlDialogue : context.yamlDialogues()) {
            Match match = bestMatch(yamlDialogue.text(), context.novel().dialogues());
            if (match.score() >= 0.85) {
                accepted++;
            } else if (match.score() >= 0.60) {
                accepted++;
                issues.add(issue("Dialogue Precision", "warning", "对白有偏差",
                        "YAML 对白与原文对白存在明显差异，相似度 %.2f".formatted(match.score()),
                        yamlDialogue.sceneId(), yamlDialogue.chapterIndex(), null, null,
                        yamlDialogue.text(), match.dialogue() == null ? null : match.dialogue().text(), "核对并尽量保持原文对白"));
            } else if (EvaluationTextUtil.containsNormalized(context.novel().fullText(), yamlDialogue.text())
                    && !EvaluationTextUtil.containsAsDirectQuote(context.novel().fullText(), yamlDialogue.text())) {
                issues.add(issue("Dialogue Precision", "error", "叙事误转对白",
                        "YAML dialogue 文本存在于原文叙事中，但不是直接对白",
                        yamlDialogue.sceneId(), yamlDialogue.chapterIndex(), null, null,
                        yamlDialogue.text(), null, "改为 action，或删除该 dialogue"));
            } else {
                issues.add(issue("Dialogue Precision", "error", "疑似编造",
                        "YAML dialogue 未在原文对白中找到可靠依据",
                        yamlDialogue.sceneId(), yamlDialogue.chapterIndex(), null, null,
                        yamlDialogue.text(), match.dialogue() == null ? null : match.dialogue().text(), "删除或改写为原文已有对白"));
            }
        }
        int total = context.yamlDialogues().size();
        return new EvaluationCheckResult(
                metric("dialogue_precision", "对白精确率", accepted, total, "YAML 对白可信 %d/%d".formatted(accepted, total)),
                issues
        );
    }

    private Match bestMatch(String text, List<NovelDialogueData> novelDialogues) {
        double bestScore = 0;
        NovelDialogueData best = null;
        for (NovelDialogueData dialogue : novelDialogues) {
            double score = EvaluationTextUtil.similarity(text, dialogue.text());
            if (score > bestScore) {
                bestScore = score;
                best = dialogue;
            }
        }
        return new Match(best, bestScore);
    }

    private record Match(NovelDialogueData dialogue, double score) {
    }
}
