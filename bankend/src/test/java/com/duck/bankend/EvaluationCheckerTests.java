package com.duck.bankend;

import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.evaluation.EvaluationCheckResult;
import com.duck.bankend.model.evaluation.EvaluationContext;
import com.duck.bankend.model.evaluation.NovelEvaluationData;
import com.duck.bankend.model.evaluation.ScriptYamlData;
import com.duck.bankend.model.evaluation.YamlBeatData;
import com.duck.bankend.model.evaluation.YamlCharacterData;
import com.duck.bankend.model.evaluation.YamlDialogueData;
import com.duck.bankend.service.evaluation.NovelEvaluationParser;
import com.duck.bankend.service.evaluation.ScriptYamlEvaluationParser;
import com.duck.bankend.service.evaluation.checker.ActionCoverageChecker;
import com.duck.bankend.service.evaluation.checker.CharacterConsistencyChecker;
import com.duck.bankend.service.evaluation.checker.DialoguePrecisionChecker;
import com.duck.bankend.service.evaluation.checker.DialogueRecallChecker;
import com.duck.bankend.service.evaluation.checker.StructureChecker;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationCheckerTests {

    private final NovelEvaluationParser novelParser = new NovelEvaluationParser();
    private final ScriptYamlEvaluationParser yamlParser = new ScriptYamlEvaluationParser();

    @Test
    void checksDialogueRecallPrecisionActionCharacterAndStructureIssues() {
        EvaluationContext context = context("""
                # 倒影

                ## 第一章 初遇

                林屿安说：“我在听。”

                他心里默念密码正确。

                ## 第二章 重逢

                顾衍川说：“去我办公室坐坐？”
                """, """
                schema_version: "1.0"
                metadata:
                  title: "倒影"
                characters:
                  - id: "character-lin"
                    name: "林屿安（意识体）"
                  - id: "character-lin-2"
                    name: "林屿安"
                scenes:
                  - id: "bad-scene"
                    order: 1
                    chapter:
                      index: 1
                      title: "第一章 初遇"
                    title: "初遇"
                    location: "咖啡厅"
                    time_of_day: "白天"
                    summary: "林屿安在咖啡厅。"
                    characters:
                      - "character-missing"
                    beats:
                      - type: "action"
                        text: ""
                      - type: "dialogue"
                        character_id: "character-lin"
                        character_name: "林屿安"
                        text: "密码正确"
                    source_refs:
                      - chapter_index: 1
                        chapter_title: "第一章 初遇"
                        chunk_index: 1
                        paragraph_start: 1
                        paragraph_end: 2
                """);

        EvaluationCheckResult recall = new DialogueRecallChecker().check(context);
        EvaluationCheckResult precision = new DialoguePrecisionChecker().check(context);
        EvaluationCheckResult action = new ActionCoverageChecker().check(context);
        EvaluationCheckResult character = new CharacterConsistencyChecker().check(context);
        EvaluationCheckResult structure = new StructureChecker().check(context);

        assertThat(recall.issues()).extracting("type").contains("对白遗漏");
        assertThat(recall.metric().getSummary()).contains("YAML 对白利用饱和度");
        assertThat(precision.issues()).extracting("type").contains("叙事误转对白");
        assertThat(action.issues()).extracting("type").contains("空动作描写", "场景完全无动作描写");
        assertThat(character.issues()).extracting("type").contains("角色重复", "引用了未定义的角色");
        assertThat(character.metric().getNumerator()).isEqualTo(1);
        assertThat(character.metric().getDenominator()).isEqualTo(2);
        assertThat(structure.issues()).extracting("type").contains("scene_id 格式异常", "场景 beat 数过少", "有章节完全未被覆盖");
        assertThat(structure.metric().getSummary()).contains("健康场景 0/1", "章节覆盖 1/3");
    }

    private EvaluationContext context(String novelText, String yamlText) {
        Novel novel = new Novel();
        novel.setId(1L);
        novel.setTitle("倒影");
        novel.setContent(novelText);
        NovelEvaluationData novelData = novelParser.parse(novel);
        ScriptYamlData yamlData = yamlParser.parse(yamlText);
        Map<String, YamlCharacterData> charactersById = new LinkedHashMap<>();
        for (YamlCharacterData character : yamlData.characters()) {
            charactersById.put(character.id(), character);
        }
        List<YamlDialogueData> yamlDialogues = yamlData.scenes().stream()
                .flatMap(scene -> scene.beats().stream()
                        .filter(beat -> "dialogue".equals(beat.type()))
                        .map(beat -> dialogue(scene.id(), scene.chapterIndex(), beat)))
                .toList();
        Set<Integer> novelChapterIndexes = new LinkedHashSet<>();
        novelData.chapters().forEach(chapter -> novelChapterIndexes.add(chapter.index()));
        return new EvaluationContext(novelData, yamlData, yamlDialogues, charactersById, novelChapterIndexes);
    }

    private YamlDialogueData dialogue(String sceneId, int chapterIndex, YamlBeatData beat) {
        return new YamlDialogueData(beat.text(), sceneId, beat.characterId(), beat.characterName(), chapterIndex);
    }
}
