package com.duck.bankend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.duck.bankend.mapper.NovelChapterMapper;
import com.duck.bankend.mapper.NovelChunkMapper;
import com.duck.bankend.mapper.ScriptDialogueMapper;
import com.duck.bankend.mapper.ScriptGenerationTaskMapper;
import com.duck.bankend.mapper.ScriptSceneMapper;
import com.duck.bankend.model.dto.ScriptGenerationOverview;
import com.duck.bankend.model.dto.ScriptGenerationTaskView;
import com.duck.bankend.model.dto.ScriptSceneView;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.entity.NovelChapter;
import com.duck.bankend.model.entity.NovelChunk;
import com.duck.bankend.model.entity.ScriptDialogue;
import com.duck.bankend.model.entity.ScriptGenerationTask;
import com.duck.bankend.model.entity.ScriptScene;
import com.duck.bankend.service.DeepSeekSceneClient;
import com.duck.bankend.service.NovelService;
import com.duck.bankend.service.ScriptGenerationService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class ScriptGenerationServiceImpl implements ScriptGenerationService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCEEDED = "succeeded";
    private static final String STATUS_FAILED = "failed";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NovelService novelService;
    private final DeepSeekSceneClient deepSeekSceneClient;
    private final NovelChapterMapper chapterMapper;
    private final NovelChunkMapper chunkMapper;
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

        clearScenes(novelId);
        LocalDateTime now = LocalDateTime.now();
        ScriptGenerationTask task = new ScriptGenerationTask();
        task.setNovelId(novelId);
        task.setStatus(STATUS_PENDING);
        task.setTotalChunks(chunks.size());
        task.setProcessedChunks(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        executorService.submit(() -> runTask(task.getId()));
        return ScriptGenerationTaskView.from(task);
    }

    @Override
    public ScriptGenerationTaskView getLatestTask(Long novelId) {
        return ScriptGenerationTaskView.from(loadLatestTask(novelId));
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
    }

    @Override
    public ScriptGenerationOverview getOverview(Long novelId) {
        ScriptGenerationOverview overview = new ScriptGenerationOverview();
        overview.setLatestTask(getLatestTask(novelId));
        overview.setScenes(listScenes(novelId));
        return overview;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runTask(Long taskId) {
        ScriptGenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        try {
            updateTaskStatus(task, STATUS_RUNNING, null);
            Novel novel = novelService.getActiveNovel(task.getNovelId());
            Map<Long, NovelChapter> chapters = loadChapters(task.getNovelId());
            List<NovelChunk> chunks = loadChunks(task.getNovelId());
            DedupState dedupState = new DedupState();

            for (NovelChunk chunk : chunks) {
                NovelChapter chapter = chapters.get(chunk.getChapterId());
                JsonNode scenes = deepSeekSceneClient.extractScenes(novel, chapter, chunk);
                saveScenes(novel, chapter, chunk, scenes, dedupState);
                incrementProgress(task);
            }
            updateTaskStatus(task, STATUS_SUCCEEDED, null);
        } catch (Exception exception) {
            updateTaskStatus(task, STATUS_FAILED, exception.getMessage());
        }
    }

    private void saveScenes(Novel novel, NovelChapter chapter, NovelChunk chunk, JsonNode scenes, DedupState dedupState) {
        int sceneNumber = 1;
        for (JsonNode sceneNode : scenes) {
            String sceneId = textOrDefault(sceneNode, "scene_id",
                    "chapter-%d-chunk-%d-scene-%d".formatted(chunk.getChapterIndex(), chunk.getChunkIndex(), sceneNumber));
            sceneNumber++;

            if (!dedupState.sceneIds.add(sceneId)) {
                continue;
            }

            String summary = textOrDefault(sceneNode, "summary", "");
            ScriptScene similar = findSimilarScene(chapter.getId(), summary, dedupState);
            if (similar != null) {
                mergeScene(similar, sceneNode, chapter.getId(), dedupState);
                continue;
            }

            ArrayNode beats = filterDialogueDuplicates(asArray(sceneNode.get("beats")), chapter.getId(), dedupState);
            ScriptScene scene = new ScriptScene();
            LocalDateTime now = LocalDateTime.now();
            scene.setNovelId(novel.getId());
            scene.setChapterId(chapter.getId());
            scene.setChunkId(chunk.getId());
            scene.setSceneId(sceneId);
            scene.setTitle(textOrDefault(sceneNode, "title", "未命名场景"));
            scene.setLocation(textOrNull(sceneNode, "location"));
            scene.setTimeOfDay(textOrNull(sceneNode, "time_of_day"));
            scene.setSummary(summary);
            scene.setCharactersJson(writeJson(asArray(sceneNode.get("characters"))));
            scene.setBeatsJson(writeJson(beats));
            scene.setSourceRefsJson(writeJson(resolveSourceRefs(sceneNode, chapter, chunk)));
            scene.setCreatedAt(now);
            scene.setUpdatedAt(now);
            sceneMapper.insert(scene);
            saveDialogues(scene.getId(), beats);
            dedupState.scenesByChapter.computeIfAbsent(chapter.getId(), ignored -> new ArrayList<>()).add(scene);
        }
    }

    private ScriptScene findSimilarScene(Long chapterId, String summary, DedupState dedupState) {
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        for (ScriptScene scene : dedupState.scenesByChapter.getOrDefault(chapterId, List.of())) {
            if (summarySimilarity(scene.getSummary(), summary) >= 0.9) {
                return scene;
            }
        }
        return null;
    }

    private void mergeScene(ScriptScene target, JsonNode sceneNode, Long chapterId, DedupState dedupState) {
        ArrayNode mergedBeats = asArray(readJson(target.getBeatsJson()));
        ArrayNode newBeats = filterDialogueDuplicates(asArray(sceneNode.get("beats")), chapterId, dedupState);
        newBeats.forEach(mergedBeats::add);

        ArrayNode mergedSources = asArray(readJson(target.getSourceRefsJson()));
        asArray(sceneNode.get("source_refs")).forEach(mergedSources::add);

        target.setBeatsJson(writeJson(mergedBeats));
        target.setSourceRefsJson(writeJson(mergedSources));
        target.setUpdatedAt(LocalDateTime.now());
        sceneMapper.updateById(target);
        saveDialogues(target.getId(), newBeats);
    }

    private ArrayNode filterDialogueDuplicates(ArrayNode beats, Long chapterId, DedupState dedupState) {
        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode beat : beats) {
            if (!"dialogue".equals(beat.path("type").asText())) {
                filtered.add(beat);
                continue;
            }

            String characterName = dialogueCharacter(beat);
            String text = beat.path("text").asText("");
            if (!StringUtils.hasText(text)) {
                continue;
            }

            String textKey = normalize(text);
            String pairKey = normalize(characterName) + "|" + textKey;
            Set<String> textSet = dedupState.dialogueTextsByChapter.computeIfAbsent(chapterId, ignored -> new HashSet<>());
            Set<String> pairSet = dedupState.dialoguePairsByChapter.computeIfAbsent(chapterId, ignored -> new HashSet<>());
            if (textSet.contains(textKey) || pairSet.contains(pairKey)) {
                continue;
            }
            textSet.add(textKey);
            pairSet.add(pairKey);
            filtered.add(beat);
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
            dialogue.setCharacterName(dialogueCharacter(beat));
            dialogue.setText(text);
            dialogue.setCreatedAt(LocalDateTime.now());
            dialogueMapper.insert(dialogue);
        }
    }

    private ArrayNode resolveSourceRefs(JsonNode sceneNode, NovelChapter chapter, NovelChunk chunk) {
        ArrayNode sourceRefs = asArray(sceneNode.get("source_refs"));
        if (!sourceRefs.isEmpty()) {
            return sourceRefs;
        }
        ObjectNode source = objectMapper.createObjectNode();
        source.put("chapter_index", chunk.getChapterIndex());
        source.put("chapter_title", chapter.getTitle());
        source.put("chunk_index", chunk.getChunkIndex());
        source.put("paragraph_start", chunk.getParagraphStart());
        source.put("paragraph_end", chunk.getParagraphEnd());
        sourceRefs.add(source);
        return sourceRefs;
    }

    private ScriptSceneView toSceneView(ScriptScene scene) {
        List<String> characters = new ArrayList<>();
        for (JsonNode character : asArray(readJson(scene.getCharactersJson()))) {
            if (character.isTextual()) {
                characters.add(character.asText());
            } else if (character.has("name")) {
                characters.add(character.path("name").asText());
            }
        }
        int beatsCount = asArray(readJson(scene.getBeatsJson())).size();
        return ScriptSceneView.from(scene, characters, beatsCount);
    }

    private void updateTaskStatus(ScriptGenerationTask task, String status, String errorMessage) {
        task.setStatus(status);
        task.setErrorMessage(errorMessage);
        LocalDateTime now = LocalDateTime.now();
        if (STATUS_RUNNING.equals(status)) {
            task.setStartedAt(now);
        }
        if (STATUS_SUCCEEDED.equals(status) || STATUS_FAILED.equals(status)) {
            task.setCompletedAt(now);
        }
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
    }

    private void incrementProgress(ScriptGenerationTask task) {
        task.setProcessedChunks(task.getProcessedChunks() + 1);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
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
            return character;
        }
        character = beat.path("character").asText(null);
        return StringUtils.hasText(character) ? character : beat.path("character_id").asText("");
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

    private static class DedupState {
        private final Set<String> sceneIds = new LinkedHashSet<>();
        private final Map<Long, List<ScriptScene>> scenesByChapter = new HashMap<>();
        private final Map<Long, Set<String>> dialogueTextsByChapter = new HashMap<>();
        private final Map<Long, Set<String>> dialoguePairsByChapter = new HashMap<>();
    }
}
