package com.duck.bankend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duck.bankend.client.DeepSeekCharacterProfileClient;
import com.duck.bankend.client.DeepSeekSceneClient;
import com.duck.bankend.client.DeepSeekSceneClient.SceneExtractionResult;
import com.duck.bankend.constant.ScriptGenerationConst;
import com.duck.bankend.constant.ScriptGenerationStatusConst;
import com.duck.bankend.constant.DeepSeekPromptConst;
import com.duck.bankend.mapper.ScriptCharacterProfileMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.duck.bankend.mapper.NovelChapterMapper;
import com.duck.bankend.mapper.NovelChunkMapper;
import com.duck.bankend.mapper.ScriptChapterStateMapper;
import com.duck.bankend.mapper.ScriptDialogueMapper;
import com.duck.bankend.mapper.ScriptGenerationTaskMapper;
import com.duck.bankend.mapper.ScriptSceneMapper;
import com.duck.bankend.model.dto.ScriptGenerationOverview;
import com.duck.bankend.model.dto.ScriptGenerationTaskView;
import com.duck.bankend.model.dto.ScriptSceneView;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.entity.NovelChapter;
import com.duck.bankend.model.entity.NovelChunk;
import com.duck.bankend.model.entity.ScriptChapterState;
import com.duck.bankend.model.entity.ScriptCharacterProfile;
import com.duck.bankend.model.entity.ScriptDialogue;
import com.duck.bankend.model.entity.ScriptGenerationTask;
import com.duck.bankend.model.entity.ScriptScene;
import com.duck.bankend.service.NovelService;
import com.duck.bankend.service.ScriptGenerationConcurrencyLimiter;
import com.duck.bankend.service.ScriptGenerationRuntimeService;
import com.duck.bankend.service.ScriptGenerationService;
import com.duck.bankend.util.ScriptCharacterNameNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ScriptGenerationServiceImpl implements ScriptGenerationService {

    private static final Pattern DIRECT_QUOTE_PATTERN = Pattern.compile("[“\"「『‘]([^”\"」』’]+)[”\"」』’]");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExecutorService scriptGenerationExecutorService;
    private final ScriptGenerationConcurrencyLimiter concurrencyLimiter;
    private final ScriptGenerationRuntimeService runtimeService;
    private final NovelService novelService;
    private final DeepSeekSceneClient deepSeekSceneClient;
    private final DeepSeekCharacterProfileClient deepSeekCharacterProfileClient;
    private final NovelChapterMapper chapterMapper;
    private final NovelChunkMapper chunkMapper;
    private final ScriptChapterStateMapper chapterStateMapper;
    private final ScriptCharacterProfileMapper characterProfileMapper;
    private final ScriptGenerationTaskMapper taskMapper;
    private final ScriptSceneMapper sceneMapper;
    private final ScriptDialogueMapper dialogueMapper;

    @Override
    @Transactional
    public ScriptGenerationTaskView startGeneration(Long novelId) {
        Novel novel = novelService.getActiveNovel(novelId);
        if (novel == null) {
            return null;
        }
        if (!deepSeekSceneClient.isConfigured()) {
            throw new IllegalArgumentException("未配置 DEEPSEEK_API_KEY");
        }

        List<NovelChunk> chunks = loadChunks(novelId);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("请先解析章节并生成分块");
        }

        if (!runtimeService.acquireNovelLock(novelId)) {
            throw new IllegalArgumentException("当前小说已有 AI 分析任务正在运行");
        }
        if (!concurrencyLimiter.tryAcquireTask()) {
            runtimeService.releaseNovelLock(novelId);
            throw new IllegalArgumentException("当前 AI 分析任务较多，请稍后重试");
        }

        boolean runtimeBound = false;
        try {
            clearScenes(novelId);
            LocalDateTime now = LocalDateTime.now();
            ScriptGenerationTask task = new ScriptGenerationTask();
            task.setNovelId(novelId);
            task.setStatus(ScriptGenerationStatusConst.PENDING);
            task.setTotalChunks(chunks.size());
            task.setProcessedChunks(0);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskMapper.insert(task);
            runtimeService.bindTask(novelId, task.getId());
            runtimeService.saveTaskState(task, null, null);
            runtimeBound = true;

            submitTaskAfterCommit(task.getId(), novelId);
            return ScriptGenerationTaskView.from(task);
        } catch (RuntimeException exception) {
            concurrencyLimiter.releaseTask();
            if (runtimeBound) {
                runtimeService.deleteLatestTask(novelId);
            } else {
                runtimeService.releaseNovelLock(novelId);
            }
            throw exception;
        }
    }

    @Override
    public ScriptGenerationTaskView getLatestTask(Long novelId) {
        ScriptGenerationTaskView runtimeTask = runtimeService.getLatestTask(novelId);
        return runtimeTask == null ? ScriptGenerationTaskView.from(loadLatestTask(novelId)) : runtimeTask;
    }

    @Override
    public List<ScriptSceneView> listScenes(Long novelId) {
        return loadScenes(novelId).stream().map(this::toSceneView).toList();
    }

    @Override
    @Transactional
    public void clearScenes(Long novelId) {
        List<ScriptScene> scenes = loadScenes(novelId);
        if (!scenes.isEmpty()) {
            List<Long> sceneIds = scenes.stream().map(ScriptScene::getId).toList();
            dialogueMapper.delete(new LambdaQueryWrapper<ScriptDialogue>().in(ScriptDialogue::getSceneDbId, sceneIds));
        }
        sceneMapper.delete(new LambdaQueryWrapper<ScriptScene>().eq(ScriptScene::getNovelId, novelId));
        chapterStateMapper.delete(new LambdaQueryWrapper<ScriptChapterState>().eq(ScriptChapterState::getNovelId, novelId));
        characterProfileMapper.delete(new LambdaQueryWrapper<ScriptCharacterProfile>().eq(ScriptCharacterProfile::getNovelId, novelId));
    }

    @Override
    public ScriptGenerationOverview getOverview(Long novelId) {
        ScriptGenerationOverview overview = new ScriptGenerationOverview();
        overview.setLatestTask(getLatestTask(novelId));
        overview.setScenes(listScenes(novelId));
        return overview;
    }

    private void submitTaskAfterCommit(Long taskId, Long novelId) {
        Runnable submit = () -> {
            try {
                scriptGenerationExecutorService.submit(() -> runTask(taskId, novelId));
            } catch (RuntimeException exception) {
                concurrencyLimiter.releaseTask();
                ScriptGenerationTask task = taskMapper.selectById(taskId);
                if (task != null) {
                    updateTaskStatus(task, ScriptGenerationStatusConst.FAILED, "提交 AI 分析任务失败: " + exception.getMessage());
                }
                runtimeService.releaseNovelLock(novelId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        concurrencyLimiter.releaseTask();
                        runtimeService.deleteLatestTask(novelId);
                    }
                }
            });
        } else {
            submit.run();
        }
    }

    private void runTask(Long taskId, Long novelId) {
        ScriptGenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            concurrencyLimiter.releaseTask();
            runtimeService.releaseNovelLock(novelId);
            return;
        }

        try {
            updateTaskStatus(task, ScriptGenerationStatusConst.RUNNING, null);
            Novel novel = novelService.getActiveNovel(task.getNovelId());
            Map<Long, NovelChapter> chapters = loadChapters(task.getNovelId());
            List<NovelChunk> chunks = loadChunks(task.getNovelId());
            DedupState dedupState = new DedupState();
            JsonNode previousChapterState = objectMapper.createObjectNode();
            OpenSceneRef previousOpenScene = null;
            Long currentChapterId = null;

            for (NovelChunk chunk : chunks) {
                NovelChapter chapter = chapters.get(chunk.getChapterId());
                if (chapter == null) {
                    throw new IllegalStateException("chunk 缺少对应章节，请重新解析章节后再分析");
                }
                if (!chapter.getId().equals(currentChapterId)) {
                    previousChapterState = objectMapper.createObjectNode();
                    previousOpenScene = null;
                    currentChapterId = chapter.getId();
                }

                QualityCheckedExtraction extraction;
                try {
                    extraction = extractScenesWithQualityRetry(novel, chapter, chunk, previousChapterState);
                } catch (Exception exception) {
                    throw new IllegalStateException("第 %d 章第 %d 个 chunk 分析失败: %s".formatted(
                            chunk.getChapterIndex(),
                            chunk.getChunkIndex(),
                            exception.getMessage()
                    ), exception);
                }
                SaveScenesResult saveResult = saveScenes(novel, chapter, chunk, extraction.scenes(), previousOpenScene, dedupState);
                previousChapterState = normalizeChapterState(extraction.chapterState(), saveResult);
                saveChapterState(novel, chapter, chunk, previousChapterState);
                previousOpenScene = resolveOpenScene(previousChapterState, saveResult, chapter, chunk);
                incrementProgress(task, chunk);
            }
            generateCharacterProfiles(novel);
            updateTaskStatus(task, ScriptGenerationStatusConst.SUCCEEDED, null);
        } catch (Exception exception) {
            updateTaskStatus(task, ScriptGenerationStatusConst.FAILED, exception.getMessage());
        } finally {
            concurrencyLimiter.releaseTask();
            runtimeService.releaseNovelLock(novelId);
        }
    }

    private QualityCheckedExtraction extractScenesWithQualityRetry(Novel novel, NovelChapter chapter, NovelChunk chunk,
                                                                   JsonNode previousChapterState) {
        QualityCheck lastQualityCheck = null;
        for (int attempt = 0; attempt <= ScriptGenerationConst.MAX_QUALITY_RETRY; attempt++) {
            String qualityInstruction = attempt == 0
                    ? DeepSeekPromptConst.DEFAULT_QUALITY_INSTRUCTION
                    : DeepSeekPromptConst.RETRY_QUALITY_INSTRUCTION_TEMPLATE.formatted(lastQualityCheck.message());
            SceneExtractionResult extraction;
            concurrencyLimiter.acquireDeepSeekRequest();
            try {
                extraction = deepSeekSceneClient.extractScenes(novel, chapter, chunk, previousChapterState, qualityInstruction);
            } finally {
                concurrencyLimiter.releaseDeepSeekRequest();
            }
            QualityCheck qualityCheck = sanitizeAndCheckScenes(extraction.scenes(), chunk);
            if (qualityCheck.accepted()) {
                return new QualityCheckedExtraction(qualityCheck.scenes(), extraction.chapterState());
            }
            lastQualityCheck = qualityCheck;
        }
        throw new IllegalStateException("AI 输出质量不合格: " + (lastQualityCheck == null ? "未知错误" : lastQualityCheck.message()));
    }

    private QualityCheck sanitizeAndCheckScenes(JsonNode rawScenes, NovelChunk chunk) {
        ArrayNode sanitizedScenes = objectMapper.createArrayNode();
        int rawSceneCount = 0;
        int rawActionCount = 0;
        int emptyActionCount = 0;
        int validActionCount = 0;
        int dialogueCount = 0;

        for (JsonNode sceneNode : asArray(rawScenes)) {
            rawSceneCount++;
            SanitizedScene sanitizedScene = sanitizeScene(sceneNode, chunk);
            rawActionCount += sanitizedScene.rawActionCount();
            emptyActionCount += sanitizedScene.emptyActionCount();
            validActionCount += sanitizedScene.validActionCount();
            dialogueCount += sanitizedScene.dialogueCount();
            if (sanitizedScene.scene() != null) {
                sanitizedScenes.add(sanitizedScene.scene());
            }
        }

        List<String> reasons = new ArrayList<>();
        if (rawSceneCount == 0 || sanitizedScenes.isEmpty()) {
            reasons.add("未生成有效 scene");
        }
        if (rawActionCount > 0) {
            double emptyRatio = (double) emptyActionCount / rawActionCount;
            if (emptyRatio > ScriptGenerationConst.MAX_EMPTY_ACTION_RATIO) {
                reasons.add("action/transition 空文本比例过高 %d/%d".formatted(emptyActionCount, rawActionCount));
            }
        }
        if (dialogueCount > 0 && validActionCount == 0) {
            reasons.add("存在对白但没有任何有效 action");
        }
        return new QualityCheck(reasons.isEmpty(), sanitizedScenes, String.join("；", reasons));
    }

    private SanitizedScene sanitizeScene(JsonNode sceneNode, NovelChunk chunk) {
        if (sceneNode == null || !sceneNode.isObject()) {
            return new SanitizedScene(null, 0, 0, 0, 0);
        }

        ObjectNode scene = objectMapper.createObjectNode();
        copyTextField(sceneNode, scene, "scene_id");
        copyTextField(sceneNode, scene, "title");
        copyTextField(sceneNode, scene, "location");
        copyTextField(sceneNode, scene, "time_of_day");
        copyTextField(sceneNode, scene, "summary");
        copyTextField(sceneNode, scene, "continuation_of");
        copyTextField(sceneNode, scene, "continuation_reason");
        if (sceneNode.has("is_continuation")) {
            scene.put("is_continuation", sceneNode.path("is_continuation").asBoolean(false));
        }
        scene.set("source_refs", asArray(sceneNode.get("source_refs")).deepCopy());

        ArrayNode beats = objectMapper.createArrayNode();
        Set<String> characterNames = new LinkedHashSet<>();
        for (JsonNode character : asArray(sceneNode.get("characters"))) {
            String name = character.isTextual() ? character.asText() : character.path("name").asText("");
            name = ScriptCharacterNameNormalizer.displayName(name);
            if (StringUtils.hasText(name)) {
                characterNames.add(name);
            }
        }

        int rawActionCount = 0;
        int emptyActionCount = 0;
        int validActionCount = 0;
        int dialogueCount = 0;
        for (JsonNode beat : asArray(sceneNode.get("beats"))) {
            if (beat == null || !beat.isObject()) {
                continue;
            }
            String type = normalizeBeatType(textOrDefault(beat, "type", "action"));
            String text = beatText(beat);
            if ("dialogue".equals(type)) {
                dialogueCount++;
                if (!hasMeaningfulText(text)) {
                    continue;
                }
                String characterName = ScriptCharacterNameNormalizer.displayName(dialogueCharacter(beat));
                if (!StringUtils.hasText(characterName)) {
                    characterName = "未知";
                }
                characterNames.add(characterName);
                ObjectNode sanitizedBeat = objectMapper.createObjectNode();
                if (isDirectQuotedDialogue(text, chunk)) {
                    sanitizedBeat.put("type", "dialogue");
                    sanitizedBeat.put("character_name", characterName);
                    sanitizedBeat.put("text", text.trim());
                } else {
                    sanitizedBeat.put("type", "action");
                    sanitizedBeat.put("text", indirectDialogueActionText(characterName, text));
                    validActionCount++;
                }
                beats.add(sanitizedBeat);
                continue;
            }

            rawActionCount++;
            if (!hasMeaningfulText(text)) {
                emptyActionCount++;
                continue;
            }
            validActionCount++;
            ObjectNode sanitizedBeat = objectMapper.createObjectNode();
            sanitizedBeat.put("type", type);
            sanitizedBeat.put("text", text.trim());
            beats.add(sanitizedBeat);
        }

        if (beats.isEmpty()) {
            return new SanitizedScene(null, rawActionCount, emptyActionCount, validActionCount, dialogueCount);
        }
        ArrayNode characters = objectMapper.createArrayNode();
        characterNames.forEach(characters::add);
        scene.set("characters", characters);
        scene.set("beats", beats);
        return new SanitizedScene(scene, rawActionCount, emptyActionCount, validActionCount, dialogueCount);
    }

    private void copyTextField(JsonNode source, ObjectNode target, String field) {
        String value = textOrNull(source, field);
        if (StringUtils.hasText(value)) {
            target.put(field, value.trim());
        }
    }

    private SaveScenesResult saveScenes(Novel novel, NovelChapter chapter, NovelChunk chunk, JsonNode scenes,
                                        OpenSceneRef previousOpenScene, DedupState dedupState) {
        SaveScenesResult result = new SaveScenesResult();
        ArrayNode sceneArray = asArray(scenes);
        int totalScenes = Math.max(1, sceneArray.size());
        List<SourceRange> sourceRanges = resolveChunkSceneRanges(sceneArray, chunk);
        int sceneNumber = 1;
        for (JsonNode sceneNode : sceneArray) {
            int currentSceneNumber = sceneNumber;
            SourceRange sourceRange = sourceRanges.get(currentSceneNumber - 1);
            String rawSceneId = textOrDefault(sceneNode, "scene_id", "scene-%d".formatted(sceneNumber));
            String sceneId = buildSceneId(sceneNode, chunk, sceneNumber);
            sceneNumber++;

            if (sceneNumber == 2 && canMergeContinuation(sceneNode, chapter, chunk, previousOpenScene)) {
                mergeScene(previousOpenScene.scene(), sceneNode, chapter, chunk, sourceRange, dedupState);
                result.mapSceneId(rawSceneId, previousOpenScene.scene());
                result.lastTouchedScene = previousOpenScene.scene();
                continue;
            }

            if (!dedupState.sceneIds.add(sceneId)) {
                continue;
            }

            String summary = textOrDefault(sceneNode, "summary", "");
            ScriptScene similar = findSimilarScene(chapter.getId(), summary, dedupState);
            if (similar != null) {
                mergeScene(similar, sceneNode, chapter, chunk, sourceRange, dedupState);
                result.mapSceneId(rawSceneId, similar);
                result.lastTouchedScene = similar;
                continue;
            }

            ArrayNode beats = filterDialogueDuplicates(asArray(sceneNode.get("beats")), chapter.getId(), dedupState);
            if (beats.isEmpty()) {
                continue;
            }
            ScriptScene scene = new ScriptScene();
            LocalDateTime now = LocalDateTime.now();
            scene.setNovelId(novel.getId());
            scene.setChapterId(chapter.getId());
            scene.setChunkId(chunk.getId());
            scene.setSceneId(sceneId);
            scene.setTitle(uniqueSceneTitle(chapter.getId(), textOrDefault(sceneNode, "title", "未命名场景"), dedupState));
            scene.setLocation(textOrNull(sceneNode, "location"));
            scene.setTimeOfDay(textOrNull(sceneNode, "time_of_day"));
            scene.setSummary(summary);
            scene.setCharactersJson(writeJson(asArray(sceneNode.get("characters"))));
            scene.setBeatsJson(writeJson(beats));
            scene.setSourceRefsJson(writeJson(sourceRefsFromRange(chapter, chunk, sourceRange)));
            scene.setCreatedAt(now);
            scene.setUpdatedAt(now);
            sceneMapper.insert(scene);
            saveDialogues(scene.getId(), beats);
            dedupState.scenesByChapter.computeIfAbsent(chapter.getId(), ignored -> new ArrayList<>()).add(scene);
            result.mapSceneId(rawSceneId, scene);
            result.lastTouchedScene = scene;
        }
        return result;
    }

    private ScriptScene findSimilarScene(Long chapterId, String summary, DedupState dedupState) {
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        for (ScriptScene scene : dedupState.scenesByChapter.getOrDefault(chapterId, List.of())) {
            if (summarySimilarity(scene.getSummary(), summary) >= ScriptGenerationConst.SUMMARY_SIMILARITY_THRESHOLD) {
                return scene;
            }
        }
        return null;
    }

    private String buildSceneId(JsonNode sceneNode, NovelChunk chunk, int sceneNumber) {
        String rawSceneId = textOrDefault(sceneNode, "scene_id", ScriptGenerationConst.DEFAULT_SCENE_ID_TEMPLATE.formatted(sceneNumber));
        String normalized = normalizeSceneId(rawSceneId);
        if (normalized.matches(ScriptGenerationConst.SCENE_ID_WITH_SOURCE_REGEX)) {
            return normalized;
        }
        return ScriptGenerationConst.SCENE_ID_WITH_SOURCE_TEMPLATE.formatted(chunk.getChapterIndex(), chunk.getChunkIndex(), normalized);
    }

    private String normalizeSceneId(String sceneId) {
        String normalized = sceneId == null ? "" : sceneId.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9\\u4e00-\\u9fa5_-]+", "-");
        normalized = normalized.replaceAll("-+", "-").replaceAll("^-|-$", "");
        return StringUtils.hasText(normalized) ? normalized : "scene";
    }

    private boolean canMergeContinuation(JsonNode sceneNode, NovelChapter chapter, NovelChunk chunk, OpenSceneRef previousOpenScene) {
        if (previousOpenScene == null || !sceneNode.path("is_continuation").asBoolean(false)) {
            return false;
        }
        if (!chapter.getId().equals(previousOpenScene.chapterId())) {
            return false;
        }
        if (chunk.getChapterIndex() != previousOpenScene.chapterIndex() || chunk.getChunkIndex() != previousOpenScene.chunkIndex() + 1) {
            return false;
        }

        String rawContinuationOf = textOrNull(sceneNode, "continuation_of");
        String continuationOf = normalizeSceneId(rawContinuationOf);
        String targetSceneId = normalizeSceneId(previousOpenScene.scene().getSceneId());
        String stateSceneId = normalizeSceneId(previousOpenScene.stateSceneId());
        if (StringUtils.hasText(rawContinuationOf)
                && !continuationOf.equals(targetSceneId)
                && !continuationOf.equals(stateSceneId)) {
            return false;
        }

        if (hasConflict(previousOpenScene.scene().getLocation(), textOrNull(sceneNode, "location"))) {
            return false;
        }
        if (hasConflict(previousOpenScene.scene().getTimeOfDay(), textOrNull(sceneNode, "time_of_day"))) {
            return false;
        }
        return charactersCompatible(previousOpenScene.scene(), sceneNode);
    }

    private boolean hasConflict(String existing, String incoming) {
        if (!StringUtils.hasText(existing) || !StringUtils.hasText(incoming)) {
            return false;
        }
        return !normalize(existing).equals(normalize(incoming));
    }

    private boolean charactersCompatible(ScriptScene existing, JsonNode incomingScene) {
        Set<String> existingCharacters = characterSet(asArray(readJson(existing.getCharactersJson())));
        Set<String> incomingCharacters = characterSet(asArray(incomingScene.get("characters")));
        if (existingCharacters.isEmpty() || incomingCharacters.isEmpty()) {
            return true;
        }
        for (String character : incomingCharacters) {
            if (existingCharacters.contains(character)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> characterSet(ArrayNode characters) {
        Set<String> names = new HashSet<>();
        for (JsonNode character : characters) {
            String name = character.isTextual() ? character.asText() : character.path("name").asText("");
            if (StringUtils.hasText(name)) {
                names.add(ScriptCharacterNameNormalizer.key(name));
            }
        }
        return names;
    }

    private void mergeScene(ScriptScene target, JsonNode sceneNode, NovelChapter chapter, NovelChunk chunk,
                            SourceRange sourceRange, DedupState dedupState) {
        ArrayNode mergedBeats = asArray(readJson(target.getBeatsJson()));
        ArrayNode newBeats = filterDialogueDuplicates(asArray(sceneNode.get("beats")), chapter.getId(), dedupState);
        newBeats.forEach(mergedBeats::add);

        ArrayNode mergedCharacters = asArray(readJson(target.getCharactersJson()));
        appendUniqueCharacters(mergedCharacters, asArray(sceneNode.get("characters")));

        ArrayNode mergedSources = asArray(readJson(target.getSourceRefsJson()));
        appendUniqueJson(mergedSources, sourceRefsFromRange(chapter, chunk, sourceRange));

        target.setBeatsJson(writeJson(mergedBeats));
        target.setCharactersJson(writeJson(mergedCharacters));
        target.setSourceRefsJson(writeJson(mergedSources));
        String incomingSummary = textOrNull(sceneNode, "summary");
        if (StringUtils.hasText(incomingSummary) && !normalize(incomingSummary).equals(normalize(target.getSummary()))) {
            target.setSummary(mergeSummary(target.getSummary(), incomingSummary));
        }
        target.setUpdatedAt(LocalDateTime.now());
        sceneMapper.updateById(target);
        saveDialogues(target.getId(), newBeats);
    }

    private String uniqueSceneTitle(Long chapterId, String title, DedupState dedupState) {
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : "未命名场景";
        String key = normalize(normalizedTitle);
        Map<String, Integer> titleCounts = dedupState.sceneTitleCountsByChapter.computeIfAbsent(chapterId, ignored -> new HashMap<>());
        int count = titleCounts.getOrDefault(key, 0) + 1;
        titleCounts.put(key, count);
        return count == 1 ? normalizedTitle : normalizedTitle + "（" + count + "）";
    }

    private String mergeSummary(String existing, String incoming) {
        if (!StringUtils.hasText(existing)) {
            return incoming;
        }
        if (!StringUtils.hasText(incoming)) {
            return existing;
        }
        return existing + "\n" + incoming;
    }

    private void appendUniqueJson(ArrayNode target, ArrayNode additions) {
        Set<String> existing = new HashSet<>();
        target.forEach(item -> existing.add(item.toString()));
        additions.forEach(item -> {
            if (existing.add(item.toString())) {
                target.add(item);
            }
        });
    }

    private void appendUniqueCharacters(ArrayNode target, ArrayNode additions) {
        Set<String> existing = new HashSet<>();
        target.forEach(item -> existing.add(ScriptCharacterNameNormalizer.key(item.isTextual()
                ? item.asText()
                : item.path("name").asText(""))));
        additions.forEach(item -> {
            String name = ScriptCharacterNameNormalizer.displayName(item.isTextual()
                    ? item.asText()
                    : item.path("name").asText(""));
            if (StringUtils.hasText(name) && existing.add(ScriptCharacterNameNormalizer.key(name))) {
                target.add(name);
            }
        });
    }

    private JsonNode normalizeChapterState(JsonNode chapterState, SaveScenesResult saveResult) {
        ObjectNode state = chapterState != null && chapterState.isObject()
                ? (ObjectNode) chapterState.deepCopy()
                : objectMapper.createObjectNode();
        ensureTextField(state, "current_location");
        ensureTextField(state, "current_conflict");
        ensureArrayField(state, "active_characters");
        ensureArrayField(state, "completed_events");
        ensureArrayField(state, "unresolved_questions");

        JsonNode openSceneNode = state.get("open_scene");
        ObjectNode openScene = openSceneNode != null && openSceneNode.isObject()
                ? (ObjectNode) openSceneNode.deepCopy()
                : objectMapper.createObjectNode();
        state.set("open_scene", openScene);

        ScriptScene mappedScene = null;
        String openSceneId = textOrNull(openScene, "scene_id");
        if (StringUtils.hasText(openSceneId)) {
            mappedScene = saveResult.findScene(openSceneId);
        }
        if (mappedScene == null && !openScene.path("is_resolved").asBoolean(false)) {
            mappedScene = saveResult.lastTouchedScene;
        }
        if (mappedScene != null) {
            openScene.put("scene_id", mappedScene.getSceneId());
            String openSummary = textOrNull(openScene, "summary");
            if (StringUtils.hasText(openSummary) && !normalize(openSummary).equals(normalize(mappedScene.getSummary()))) {
                mappedScene.setSummary(openSummary);
                mappedScene.setUpdatedAt(LocalDateTime.now());
                sceneMapper.updateById(mappedScene);
            }
        }
        ensureTextField(openScene, "scene_id");
        ensureTextField(openScene, "title");
        ensureTextField(openScene, "location");
        ensureTextField(openScene, "time_of_day");
        ensureArrayField(openScene, "characters");
        ensureTextField(openScene, "summary");
        if (!openScene.has("is_resolved") || !openScene.get("is_resolved").isBoolean()) {
            openScene.put("is_resolved", true);
        }
        return state;
    }

    private void ensureTextField(ObjectNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            node.put(field, "");
        }
    }

    private void ensureArrayField(ObjectNode node, String field) {
        if (!node.has(field) || !node.get(field).isArray()) {
            node.set(field, objectMapper.createArrayNode());
        }
    }

    private void saveChapterState(Novel novel, NovelChapter chapter, NovelChunk chunk, JsonNode chapterState) {
        ScriptChapterState state = new ScriptChapterState();
        LocalDateTime now = LocalDateTime.now();
        state.setNovelId(novel.getId());
        state.setChapterId(chapter.getId());
        state.setChunkId(chunk.getId());
        state.setChapterIndex(chunk.getChapterIndex());
        state.setChunkIndex(chunk.getChunkIndex());
        state.setStateJson(writeJson(chapterState));
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        chapterStateMapper.insert(state);
    }

    private OpenSceneRef resolveOpenScene(JsonNode chapterState, SaveScenesResult saveResult, NovelChapter chapter, NovelChunk chunk) {
        JsonNode openScene = chapterState == null ? null : chapterState.path("open_scene");
        if (openScene == null || !openScene.isObject() || openScene.path("is_resolved").asBoolean(true)) {
            return null;
        }
        String sceneId = textOrNull(openScene, "scene_id");
        if (!StringUtils.hasText(sceneId)) {
            return null;
        }
        ScriptScene scene = saveResult.findScene(sceneId);
        if (scene == null) {
            scene = loadSceneBySceneId(chapter.getId(), sceneId);
        }
        return scene == null ? null : new OpenSceneRef(scene, chapter.getId(), chunk.getChapterIndex(), chunk.getChunkIndex(), sceneId);
    }

    private ScriptScene loadSceneBySceneId(Long chapterId, String sceneId) {
        return sceneMapper.selectList(new LambdaQueryWrapper<ScriptScene>()
                        .eq(ScriptScene::getChapterId, chapterId)
                        .eq(ScriptScene::getSceneId, sceneId)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ArrayNode filterDialogueDuplicates(ArrayNode beats, Long chapterId, DedupState dedupState) {
        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode beat : beats) {
            if (!"dialogue".equals(beat.path("type").asText())) {
                if (hasMeaningfulText(beatText(beat))) {
                    filtered.add(beat);
                }
                continue;
            }

            String characterName = dialogueCharacter(beat);
            String text = beat.path("text").asText("");
            if (!StringUtils.hasText(text)) {
                continue;
            }

            String textKey = normalize(text);
            String pairKey = ScriptCharacterNameNormalizer.key(characterName) + "|" + textKey;
            Set<String> textSet = dedupState.dialogueTextsByChapter.computeIfAbsent(chapterId, ignored -> new HashSet<>());
            Set<String> pairSet = dedupState.dialoguePairsByChapter.computeIfAbsent(chapterId, ignored -> new HashSet<>());
            if (textSet.contains(textKey) || pairSet.contains(pairKey)) {
                continue;
            }
            textSet.add(textKey);
            pairSet.add(pairKey);
            ObjectNode sanitizedBeat = beat.deepCopy();
            sanitizedBeat.put("character_name", ScriptCharacterNameNormalizer.displayName(characterName));
            sanitizedBeat.put("text", text.trim());
            filtered.add(sanitizedBeat);
        }
        return filtered;
    }

    private void saveDialogues(Long sceneDbId, ArrayNode beats) {
        for (JsonNode beat : beats) {
            if (!"dialogue".equals(beat.path("type").asText())) {
                continue;
            }
            String text = beat.path("text").asText("");
            if (!StringUtils.hasText(text)) {
                continue;
            }
            ScriptDialogue dialogue = new ScriptDialogue();
            dialogue.setSceneDbId(sceneDbId);
            dialogue.setCharacterName(ScriptCharacterNameNormalizer.displayName(dialogueCharacter(beat)));
            dialogue.setText(text);
            dialogue.setCreatedAt(LocalDateTime.now());
            dialogueMapper.insert(dialogue);
        }
    }

    private List<SourceRange> resolveChunkSceneRanges(ArrayNode scenes, NovelChunk chunk) {
        SourceRange chunkRange = chunkSourceRange(chunk);
        int totalScenes = Math.max(1, scenes.size());
        if (totalScenes == 1) {
            JsonNode sceneNode = scenes.isEmpty() ? objectMapper.createObjectNode() : scenes.get(0);
            return List.of(resolveInitialSceneRange(sceneNode, chunk, 1, 1).clamp(chunkRange));
        }

        List<SourceRange> ranges = new ArrayList<>();
        int sceneNumber = 1;
        for (JsonNode sceneNode : scenes) {
            ranges.add(resolveInitialSceneRange(sceneNode, chunk, sceneNumber, totalScenes).clamp(chunkRange));
            sceneNumber++;
        }
        return smoothChunkSceneRanges(ranges, chunkRange);
    }

    private SourceRange resolveInitialSceneRange(JsonNode sceneNode, NovelChunk chunk, int sceneNumber, int totalScenes) {
        SourceRange chunkRange = chunkSourceRange(chunk);
        SourceRange aiRange = mergeSourceRefs(asArray(sceneNode.get("source_refs")), chunkRange);
        if (aiRange != null && !aiRange.isDegenerateForChunk(chunkRange) && !isSuspiciouslyNarrow(aiRange, sceneNode, chunkRange, totalScenes)) {
            return aiRange;
        }
        return inferSceneSourceRange(sceneNode, chunk, sceneNumber, totalScenes);
    }

    private SourceRange mergeSourceRefs(ArrayNode sourceRefs, SourceRange chunkRange) {
        SourceRange range = null;
        for (JsonNode ref : sourceRefs) {
            range = mergeRange(range, sourceRange(ref, chunkRange));
        }
        return range == null ? null : range.clamp(chunkRange);
    }

    private boolean isSuspiciouslyNarrow(SourceRange range, JsonNode sceneNode, SourceRange chunkRange, int totalScenes) {
        if (totalScenes <= 1 || range.size() > 1 || chunkRange.size() <= totalScenes * 2) {
            return false;
        }
        int meaningfulBeats = 0;
        for (JsonNode beat : asArray(sceneNode.get("beats"))) {
            if (hasMeaningfulText(beatText(beat))) {
                meaningfulBeats++;
            }
        }
        return meaningfulBeats >= 2;
    }

    private List<SourceRange> smoothChunkSceneRanges(List<SourceRange> ranges, SourceRange chunkRange) {
        List<SourceRange> smoothed = new ArrayList<>();
        int totalScenes = ranges.size();
        int previousStart = chunkRange.start();
        for (int i = 0; i < totalScenes; i++) {
            SourceRange range = ranges.get(i).clamp(chunkRange);
            if (range.isDegenerateForChunk(chunkRange)) {
                range = fallbackSceneRange(chunkRange, i + 1, totalScenes);
            }
            SourceRange fallback = fallbackSceneRange(chunkRange, i + 1, totalScenes);
            if (range.size() == 1 && fallback.size() > 1) {
                int expandedStart = Math.min(range.start(), fallback.start());
                int expandedEnd = Math.max(range.end(), Math.min(fallback.end(), range.end() + Math.max(1, fallback.size() / 2)));
                range = new SourceRange(expandedStart, expandedEnd);
            }
            if (i > 0 && range.end() < previousStart) {
                range = fallback;
            } else if (i > 0 && range.start() < previousStart && range.end() >= previousStart) {
                range = new SourceRange(previousStart, range.end());
            }
            range = range.clamp(chunkRange);
            smoothed.add(range);
            previousStart = Math.max(previousStart, range.start());
        }
        return smoothed;
    }

    private ArrayNode sourceRefsFromRange(NovelChapter chapter, NovelChunk chunk, SourceRange sourceRange) {
        ArrayNode sourceRefs = objectMapper.createArrayNode();
        sourceRefs.add(sourceRef(chapter, chunk, sourceRange.clamp(chunkSourceRange(chunk))));
        return sourceRefs;
    }

    private SourceRange inferSceneSourceRange(JsonNode sceneNode, NovelChunk chunk, int sceneNumber, int totalScenes) {
        List<String> paragraphs = splitChunkParagraphs(chunk);
        SourceRange chunkRange = chunkSourceRange(chunk);
        if (paragraphs.isEmpty()) {
            return chunkRange;
        }

        SourceRange matchedRange = matchSceneRange(sceneNode, paragraphs, chunkRange.start());
        if (matchedRange != null) {
            return matchedRange.clamp(chunkRange);
        }
        return fallbackSceneRange(chunkRange, sceneNumber, totalScenes);
    }

    private SourceRange matchSceneRange(JsonNode sceneNode, List<String> paragraphs, int paragraphOffset) {
        SourceRange dialogueRange = null;
        SourceRange actionRange = null;
        for (JsonNode beat : asArray(sceneNode.get("beats"))) {
            String text = beatText(beat);
            if (!hasMeaningfulText(text)) {
                continue;
            }
            SourceRange range = matchTextRange(text, paragraphs, paragraphOffset);
            if (range == null) {
                continue;
            }
            if ("dialogue".equals(beat.path("type").asText())) {
                dialogueRange = mergeRange(dialogueRange, range);
            } else {
                actionRange = mergeRange(actionRange, range);
            }
        }
        return dialogueRange == null ? actionRange : mergeRange(dialogueRange, actionRange);
    }

    private SourceRange matchTextRange(String text, List<String> paragraphs, int paragraphOffset) {
        List<String> fragments = matchFragments(text);
        if (fragments.isEmpty()) {
            return null;
        }
        SourceRange range = null;
        for (int i = 0; i < paragraphs.size(); i++) {
            String normalizedParagraph = normalize(paragraphs.get(i));
            if (!StringUtils.hasText(normalizedParagraph)) {
                continue;
            }
            for (String fragment : fragments) {
                if (normalizedParagraph.contains(fragment) || fragment.contains(normalizedParagraph)) {
                    int paragraphNumber = paragraphOffset + i;
                    range = mergeRange(range, new SourceRange(paragraphNumber, paragraphNumber));
                    break;
                }
            }
        }
        return range;
    }

    private List<String> matchFragments(String text) {
        String normalizedText = normalize(text);
        if (!StringUtils.hasText(normalizedText)) {
            return List.of();
        }
        LinkedHashSet<String> fragments = new LinkedHashSet<>();
        if (normalizedText.length() >= 4) {
            fragments.add(normalizedText);
        }
        for (String part : text.split("[，。！？；、,.!?;：:“”\"'（）()《》【】\\s]+")) {
            String normalizedPart = normalize(part);
            if (normalizedPart.length() >= 6) {
                fragments.add(normalizedPart);
            }
        }
        int window = Math.min(14, normalizedText.length());
        if (window >= 8) {
            int step = Math.max(4, window / 2);
            for (int start = 0; start + window <= normalizedText.length(); start += step) {
                fragments.add(normalizedText.substring(start, start + window));
            }
            if (normalizedText.length() > window) {
                fragments.add(normalizedText.substring(normalizedText.length() - window));
            }
        }
        return new ArrayList<>(fragments);
    }

    private SourceRange fallbackSceneRange(SourceRange chunkRange, int sceneNumber, int totalScenes) {
        if (totalScenes <= 1 || chunkRange.size() <= 1) {
            return chunkRange;
        }
        int scenes = Math.max(1, totalScenes);
        int index = Math.max(1, Math.min(sceneNumber, scenes)) - 1;
        int start = chunkRange.start() + (int) Math.floor((double) chunkRange.size() * index / scenes);
        int nextStart = chunkRange.start() + (int) Math.floor((double) chunkRange.size() * (index + 1) / scenes);
        int end = Math.max(start, nextStart - 1);
        if (index == scenes - 1) {
            end = chunkRange.end();
        }
        return new SourceRange(start, Math.min(end, chunkRange.end()));
    }

    private SourceRange mergeRange(SourceRange left, SourceRange right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return new SourceRange(Math.min(left.start(), right.start()), Math.max(left.end(), right.end()));
    }

    private List<String> splitChunkParagraphs(NovelChunk chunk) {
        if (!StringUtils.hasText(chunk.getContent())) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : chunk.getContent().trim().split("\\n\\s*\\n+")) {
            if (StringUtils.hasText(paragraph)) {
                paragraphs.add(paragraph.trim());
            }
        }
        return paragraphs;
    }

    private SourceRange sourceRange(JsonNode ref, SourceRange fallback) {
        if (ref == null) {
            return fallback;
        }
        return new SourceRange(
                ref.path("paragraph_start").asInt(fallback.start()),
                ref.path("paragraph_end").asInt(fallback.end())
        ).clamp(fallback);
    }

    private SourceRange chunkSourceRange(NovelChunk chunk) {
        int paragraphStart = chunk.getParagraphStart() == null ? 0 : chunk.getParagraphStart();
        int paragraphEnd = chunk.getParagraphEnd() == null ? paragraphStart : chunk.getParagraphEnd();
        return new SourceRange(paragraphStart, Math.max(paragraphStart, paragraphEnd));
    }

    private ObjectNode sourceRef(NovelChapter chapter, NovelChunk chunk, SourceRange range) {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("chapter_index", chunk.getChapterIndex());
        source.put("chapter_title", chapter.getTitle());
        source.put("chunk_index", chunk.getChunkIndex());
        source.put("paragraph_start", range.start());
        source.put("paragraph_end", range.end());
        return source;
    }

    private ScriptSceneView toSceneView(ScriptScene scene) {
        Set<String> characters = new LinkedHashSet<>();
        for (JsonNode character : asArray(readJson(scene.getCharactersJson()))) {
            String name = "";
            if (character.isTextual()) {
                name = character.asText();
            } else if (character.has("name")) {
                name = character.path("name").asText();
            }
            name = ScriptCharacterNameNormalizer.displayName(name);
            if (StringUtils.hasText(name)) {
                characters.add(name);
            }
        }
        int beatsCount = asArray(readJson(scene.getBeatsJson())).size();
        return ScriptSceneView.from(scene, new ArrayList<>(characters), beatsCount);
    }

    private void updateTaskStatus(ScriptGenerationTask task, String status, String errorMessage) {
        task.setStatus(status);
        task.setErrorMessage(errorMessage);
        LocalDateTime now = LocalDateTime.now();
        if (ScriptGenerationStatusConst.RUNNING.equals(status)) {
            task.setStartedAt(now);
        }
        if (ScriptGenerationStatusConst.SUCCEEDED.equals(status) || ScriptGenerationStatusConst.FAILED.equals(status)) {
            task.setCompletedAt(now);
        }
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        runtimeService.saveTaskState(task, null, null);
    }

    private void incrementProgress(ScriptGenerationTask task, NovelChunk chunk) {
        task.setProcessedChunks(task.getProcessedChunks() + 1);
        task.setUpdatedAt(LocalDateTime.now());
        runtimeService.saveTaskState(task, chunk.getChapterIndex(), chunk.getChunkIndex());
    }

    private ScriptGenerationTask loadLatestTask(Long novelId) {
        return taskMapper.selectList(new LambdaQueryWrapper<ScriptGenerationTask>()
                        .eq(ScriptGenerationTask::getNovelId, novelId)
                        .orderByDesc(ScriptGenerationTask::getCreatedAt)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<ScriptScene> loadScenes(Long novelId) {
        return sceneMapper.selectList(new LambdaQueryWrapper<ScriptScene>()
                .eq(ScriptScene::getNovelId, novelId)
                .orderByAsc(ScriptScene::getChapterId)
                .orderByAsc(ScriptScene::getChunkId)
                .orderByAsc(ScriptScene::getId));
    }

    private void generateCharacterProfiles(Novel novel) {
        if (!deepSeekCharacterProfileClient.isConfigured()) {
            return;
        }
        List<ScriptScene> scenes = loadScenes(novel.getId());
        Map<String, String> knownCharacters = collectKnownCharacters(scenes);
        if (knownCharacters.isEmpty()) {
            return;
        }
        try {
            JsonNode profiles;
            concurrencyLimiter.acquireDeepSeekRequest();
            try {
                profiles = deepSeekCharacterProfileClient.generateProfiles(buildCharacterProfileInput(scenes));
            } finally {
                concurrencyLimiter.releaseDeepSeekRequest();
            }
            saveCharacterProfiles(novel.getId(), profiles, knownCharacters);
        } catch (Exception ignored) {
            // 角色画像是附加信息，生成失败不阻塞主流程，并保留上一次成功结果。
        }
    }

    private String buildCharacterProfileInput(List<ScriptScene> scenes) {
        ArrayNode items = objectMapper.createArrayNode();
        for (ScriptScene scene : scenes) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("scene_id", scene.getSceneId());
            item.put("title", scene.getTitle());
            item.put("location", scene.getLocation());
            item.put("time_of_day", scene.getTimeOfDay());
            item.put("summary", scene.getSummary());
            item.set("characters", readJson(scene.getCharactersJson()));
            item.set("dialogue_speakers", dialogueSpeakers(scene));
            items.add(item);
        }
        return writeJson(items);
    }

    private Map<String, String> collectKnownCharacters(List<ScriptScene> scenes) {
        Map<String, String> characters = new HashMap<>();
        for (ScriptScene scene : scenes) {
            collectCharacterNames(scene).forEach(name -> characters.putIfAbsent(ScriptCharacterNameNormalizer.key(name), name));
        }
        return characters;
    }

    private List<String> collectCharacterNames(ScriptScene scene) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode character : asArray(readJson(scene.getCharactersJson()))) {
            String name = ScriptCharacterNameNormalizer.displayName(character.isTextual() ? character.asText() : character.path("name").asText(""));
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        for (JsonNode beat : asArray(readJson(scene.getBeatsJson()))) {
            if ("dialogue".equals(beat.path("type").asText())) {
                String name = dialogueCharacter(beat);
                if (StringUtils.hasText(name)) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private ArrayNode dialogueSpeakers(ScriptScene scene) {
        ArrayNode speakers = objectMapper.createArrayNode();
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode beat : asArray(readJson(scene.getBeatsJson()))) {
            if ("dialogue".equals(beat.path("type").asText())) {
                String name = dialogueCharacter(beat);
                if (StringUtils.hasText(name) && names.add(name)) {
                    speakers.add(name);
                }
            }
        }
        return speakers;
    }

    private void saveCharacterProfiles(Long novelId, JsonNode profiles, Map<String, String> knownCharacters) {
        characterProfileMapper.delete(new LambdaQueryWrapper<ScriptCharacterProfile>()
                .eq(ScriptCharacterProfile::getNovelId, novelId));
        for (JsonNode profile : asArray(profiles)) {
            String name = ScriptCharacterNameNormalizer.displayName(profile.path("name").asText(""));
            String key = ScriptCharacterNameNormalizer.key(name);
            if (!knownCharacters.containsKey(key)) {
                continue;
            }
            ScriptCharacterProfile entity = new ScriptCharacterProfile();
            entity.setNovelId(novelId);
            entity.setCharacterKey(key);
            entity.setName(knownCharacters.get(key));
            entity.setAliasesJson(writeJson(asArray(profile.get("aliases"))));
            entity.setRole(textOrDefault(profile, "role", ""));
            entity.setDescription(textOrDefault(profile, "description", ""));
            characterProfileMapper.insert(entity);
        }
    }

    private List<NovelChunk> loadChunks(Long novelId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<NovelChunk>()
                .eq(NovelChunk::getNovelId, novelId)
                .orderByAsc(NovelChunk::getChapterIndex)
                .orderByAsc(NovelChunk::getChunkIndex));
    }

    private Map<Long, NovelChapter> loadChapters(Long novelId) {
        List<NovelChapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<NovelChapter>()
                .eq(NovelChapter::getNovelId, novelId));
        Map<Long, NovelChapter> map = new HashMap<>();
        for (NovelChapter chapter : chapters) {
            map.put(chapter.getId(), chapter);
        }
        return map;
    }

    private String dialogueCharacter(JsonNode beat) {
        String character = beat.path("character_name").asText(null);
        if (StringUtils.hasText(character)) {
            return ScriptCharacterNameNormalizer.displayName(character);
        }
        character = beat.path("character").asText(null);
        return ScriptCharacterNameNormalizer.displayName(StringUtils.hasText(character) ? character : beat.path("character_id").asText(""));
    }

    private String beatText(JsonNode beat) {
        String text = textOrNull(beat, "text");
        if (StringUtils.hasText(text)) {
            return text;
        }
        text = textOrNull(beat, "description");
        if (StringUtils.hasText(text)) {
            return text;
        }
        text = textOrNull(beat, "content");
        return StringUtils.hasText(text) ? text : "";
    }

    private boolean hasMeaningfulText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return StringUtils.hasText(value.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】—…-]+", ""));
    }

    private boolean isDirectQuotedDialogue(String text, NovelChunk chunk) {
        String normalizedText = normalize(text);
        if (!StringUtils.hasText(normalizedText) || chunk == null || !StringUtils.hasText(chunk.getContent())) {
            return false;
        }
        Matcher matcher = DIRECT_QUOTE_PATTERN.matcher(chunk.getContent());
        while (matcher.find()) {
            String quoted = normalize(matcher.group(1));
            if (!StringUtils.hasText(quoted)) {
                continue;
            }
            if (quoted.contains(normalizedText) || normalizedText.contains(quoted)) {
                return true;
            }
        }
        return false;
    }

    private String indirectDialogueActionText(String characterName, String text) {
        String speaker = StringUtils.hasText(characterName) ? characterName.trim() : "角色";
        String content = StringUtils.hasText(text) ? text.trim() : "相关内容";
        return speaker + "进行交流，原文以间接叙述呈现：" + content;
    }

    private String normalizeBeatType(String type) {
        if ("dialogue".equals(type) || "transition".equals(type)) {
            return type;
        }
        return "action";
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = textOrNull(node, field);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return objectMapper.createArrayNode();
        }
    }

    private ArrayNode asArray(JsonNode node) {
        if (node != null && node.isArray()) {
            return (ArrayNode) node;
        }
        return objectMapper.createArrayNode();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createArrayNode() : node);
        } catch (Exception exception) {
            throw new IllegalStateException("序列化 JSON 失败", exception);
        }
    }

    private double summarySimilarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return 0;
        }
        int distance = levenshtein(a, b);
        return 1.0 - (double) distance / Math.max(a.length(), b.length());
    }

    private int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[b.length()];
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】]+", "").toLowerCase();
    }

    private class SaveScenesResult {
        private final Map<String, ScriptScene> scenesBySceneId = new HashMap<>();
        private ScriptScene lastTouchedScene;

        private void mapSceneId(String rawSceneId, ScriptScene scene) {
            if (scene == null) {
                return;
            }
            scenesBySceneId.put(normalizeSceneId(rawSceneId), scene);
            scenesBySceneId.put(normalizeSceneId(scene.getSceneId()), scene);
        }

        private ScriptScene findScene(String sceneId) {
            if (!StringUtils.hasText(sceneId)) {
                return null;
            }
            return scenesBySceneId.get(normalizeSceneId(sceneId));
        }
    }

    private record OpenSceneRef(ScriptScene scene, Long chapterId, Integer chapterIndex, Integer chunkIndex, String stateSceneId) {
    }

    private record QualityCheckedExtraction(ArrayNode scenes, JsonNode chapterState) {
    }

    private record QualityCheck(boolean accepted, ArrayNode scenes, String message) {
    }

    private record SanitizedScene(ObjectNode scene, int rawActionCount, int emptyActionCount, int validActionCount, int dialogueCount) {
    }

    private record SourceRange(int start, int end) {

        private int size() {
            return Math.max(1, end - start + 1);
        }

        private boolean isDegenerateForChunk(SourceRange chunkRange) {
            if (start <= chunkRange.start() && end >= chunkRange.end()) {
                return true;
            }
            if (chunkRange.size() <= 1) {
                return false;
            }
            double coverage = (double) clamp(chunkRange).size() / chunkRange.size();
            if (coverage >= 0.8) {
                return true;
            }
            return start <= chunkRange.start() && end >= chunkRange.end() - 1;
        }

        private SourceRange clamp(SourceRange chunkRange) {
            int clampedStart = Math.max(chunkRange.start(), Math.min(start, chunkRange.end()));
            int clampedEnd = Math.max(clampedStart, Math.min(end, chunkRange.end()));
            return new SourceRange(clampedStart, clampedEnd);
        }
    }

    private static class DedupState {
        private final Set<String> sceneIds = new LinkedHashSet<>();
        private final Map<Long, List<ScriptScene>> scenesByChapter = new HashMap<>();
        private final Map<Long, Map<String, Integer>> sceneTitleCountsByChapter = new HashMap<>();
        private final Map<Long, Set<String>> dialogueTextsByChapter = new HashMap<>();
        private final Map<Long, Set<String>> dialoguePairsByChapter = new HashMap<>();
    }
}
