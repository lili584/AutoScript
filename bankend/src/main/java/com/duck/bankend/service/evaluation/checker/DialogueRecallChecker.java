package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.NovelDialogueData;
import com.duck.bankend.model.evaluation.YamlDialogueData;
import com.duck.bankend.util.EvaluationTextUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DialogueRecallChecker extends BaseEvaluationChecker {

    private static final double RECALL_THRESHOLD = 0.75;

    @Override
    public EvaluationCheckResult check(EvaluationContext context) {
        List<EvaluationIssue> issues = new ArrayList<>();
        Set<Integer> matchedYamlIndexes = new HashSet<>();
        int matched = 0;
        for (NovelDialogueData novelDialogue : context.novel().dialogues()) {
            Match match = bestMatch(novelDialogue.text(), context.yamlDialogues());
            if (match.score() >= RECALL_THRESHOLD) {
                matched++;
                if (match.index() >= 0) {
                    matchedYamlIndexes.add(match.index());
                }
            } else {
                issues.add(issue("Dialogue Recall", "error", "对白遗漏",
                        "原文对白未在 YAML dialogue 中找到足够相似的匹配",
                        null, novelDialogue.chapterIndex(), novelDialogue.paragraphNumber(), novelDialogue.paragraphNumber(),
                        null, novelDialogue.text(), "补充该对白，或检查该场景是否被遗漏"));
            }
        }
        int total = context.novel().dialogues().size();
        return new EvaluationCheckResult(
                metric("dialogue_recall", "对白召回率", matched, total,
                        "原文对白召回 %d/%d，YAML 对白利用饱和度 %d/%d".formatted(
                                matched, total, matchedYamlIndexes.size(), context.yamlDialogues().size())),
                issues
        );
    }

    private Match bestMatch(String text, List<YamlDialogueData> yamlDialogues) {
        double bestScore = 0;
        YamlDialogueData best = null;
        int bestIndex = -1;
        for (int i = 0; i < yamlDialogues.size(); i++) {
            YamlDialogueData dialogue = yamlDialogues.get(i);
            double score = EvaluationTextUtil.similarity(text, dialogue.text());
            if (score > bestScore) {
                bestScore = score;
                best = dialogue;
                bestIndex = i;
            }
        }
        return new Match(best, bestScore, bestIndex);
    }

    private record Match(YamlDialogueData dialogue, double score, int index) {
    }
}
