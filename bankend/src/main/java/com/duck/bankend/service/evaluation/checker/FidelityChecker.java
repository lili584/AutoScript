package com.duck.bankend.service.evaluation.checker;

import com.duck.bankend.model.dto.EvaluationIssue;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.NovelChapterData;
import com.duck.bankend.model.evaluation.NovelParagraphData;
import com.duck.bankend.model.evaluation.YamlBeatData;
import com.duck.bankend.model.evaluation.YamlCharacterData;
import com.duck.bankend.model.evaluation.YamlSceneData;
import com.duck.bankend.model.evaluation.YamlSourceRefData;
import com.duck.bankend.util.EvaluationTextUtil;
import com.duck.bankend.util.ScriptCharacterNameNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class FidelityChecker extends BaseEvaluationChecker {

    @Override
    public EvaluationCheckResult check(EvaluationContext context) {
        List<EvaluationIssue> issues = new ArrayList<>();
        Map<Integer, String> chapterTextByIndex = context.novel().chapters().stream()
                .collect(Collectors.toMap(NovelChapterData::index, chapter -> chapter.paragraphs().stream()
                        .map(paragraph -> paragraph.text())
                        .collect(Collectors.joining("\n")), (left, right) -> left));

        int checks = 0;
        for (YamlSceneData scene : context.yaml().scenes()) {
            String chapterText = chapterTextByIndex.getOrDefault(scene.chapterIndex(), context.novel().fullText());
            String sourceText = sourceText(context, scene, chapterText);
            if (StringUtils.hasText(scene.location())) {
                checks++;
                if (!EvaluationTextUtil.containsNormalized(sourceText, scene.location())) {
                    issues.add(issue("Fidelity", "warning", "场景地点原文无依据",
                            "location 在对应 source_refs 原文段落中没有找到明确依据",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            scene.location(), null, "核对地点是否为 AI 概括或幻觉"));
                }
            }
            if (StringUtils.hasText(scene.timeOfDay()) && EvaluationTextUtil.normalize(scene.timeOfDay()).length() >= 3) {
                checks++;
                if (!EvaluationTextUtil.containsNormalized(sourceText, scene.timeOfDay())) {
                    issues.add(issue("Fidelity", "info", "场景时间原文依据较弱",
                            "time_of_day 在对应 source_refs 原文段落中没有找到直接文本",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            scene.timeOfDay(), null, "如为合理概括可忽略，否则修正时间"));
                }
            }

            Set<String> sceneCharacterNames = sceneCharacterNames(scene, context.charactersById());
            for (YamlBeatData beat : scene.beats()) {
                if (!"dialogue".equals(beat.type())) {
                    continue;
                }
                checks++;
                String speakerKey = ScriptCharacterNameNormalizer.key(beat.characterName());
                if (StringUtils.hasText(speakerKey) && !sceneCharacterNames.contains(speakerKey)) {
                    issues.add(issue("Fidelity", "warning", "对白角色未在场景人物列表中",
                            "dialogue.character_name 未出现在 scene.characters 引用的人物中",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            beat.characterName(), beat.text(), "补充 scene.characters 或修正对白角色"));
                }
                if (EvaluationTextUtil.containsNormalized(context.novel().fullText(), beat.text())
                        && !EvaluationTextUtil.containsAsDirectQuote(context.novel().fullText(), beat.text())) {
                    issues.add(issue("Fidelity", "error", "叙事内容被转为对白",
                            "dialogue.text 在原文中存在，但不是引号包裹的直接对白",
                            scene.id(), scene.chapterIndex(), firstParagraph(scene), lastParagraph(scene),
                            beat.text(), null, narrativeDialogueSuggestion(context.novel().fullText(), beat.text())));
                }
            }
        }
        int numerator = Math.max(0, checks - issues.size());
        return new EvaluationCheckResult(
                metric("fidelity", "忠实度", numerator, checks, "忠实度问题 %d 个".formatted(issues.size())),
                issues
        );
    }

    private Set<String> sceneCharacterNames(YamlSceneData scene, Map<String, YamlCharacterData> charactersById) {
        Set<String> names = new LinkedHashSet<>();
        for (String id : scene.characters()) {
            YamlCharacterData character = charactersById.get(id);
            if (character != null) {
                names.add(ScriptCharacterNameNormalizer.key(character.name()));
            }
        }
        return names;
    }

    private String narrativeDialogueSuggestion(String fullText, String dialogueText) {
        String sentence = sentenceContaining(fullText, dialogueText);
        if (!StringUtils.hasText(sentence)) {
            return "改为 action 或删除该 dialogue";
        }
        return "该文本在原文中为间接叙述，建议删除此 dialogue，替换为 action: \"%s\"".formatted(sentence);
    }

    private String sentenceContaining(String fullText, String text) {
        int index = fullText.indexOf(text);
        if (index < 0) {
            return "";
        }
        int start = Math.max(Math.max(fullText.lastIndexOf('。', index), fullText.lastIndexOf('！', index)), fullText.lastIndexOf('？', index)) + 1;
        int end = nextSentenceEnd(fullText, index);
        String sentence = fullText.substring(start, end).trim();
        return sentence.length() > 80 ? sentence.substring(0, 80) : sentence;
    }

    private int nextSentenceEnd(String fullText, int index) {
        int end = fullText.length();
        for (char mark : new char[]{'。', '！', '？'}) {
            int found = fullText.indexOf(mark, index);
            if (found >= 0) {
                end = Math.min(end, found + 1);
            }
        }
        return end;
    }

    private String sourceText(EvaluationContext context, YamlSceneData scene, String fallbackChapterText) {
        if (scene.sourceRefs().isEmpty()) {
            return fallbackChapterText;
        }
        List<String> paragraphs = new ArrayList<>();
        for (YamlSourceRefData sourceRef : scene.sourceRefs()) {
            for (NovelParagraphData paragraph : context.novel().paragraphs()) {
                if (paragraph.chapterIndex() == sourceRef.chapterIndex()
                        && paragraph.paragraphNumber() >= sourceRef.paragraphStart()
                        && paragraph.paragraphNumber() <= sourceRef.paragraphEnd()) {
                    paragraphs.add(paragraph.text());
                }
            }
        }
        return paragraphs.isEmpty() ? fallbackChapterText : String.join("\n", paragraphs);
    }

    private Integer firstParagraph(YamlSceneData scene) {
        return scene.sourceRefs().isEmpty() ? null : scene.sourceRefs().get(0).paragraphStart();
    }

    private Integer lastParagraph(YamlSceneData scene) {
        return scene.sourceRefs().isEmpty() ? null : scene.sourceRefs().get(scene.sourceRefs().size() - 1).paragraphEnd();
    }
}
