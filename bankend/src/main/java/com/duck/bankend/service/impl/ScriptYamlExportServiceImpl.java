package com.duck.bankend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.duck.bankend.mapper.NovelChapterMapper;
import com.duck.bankend.mapper.ScriptCharacterProfileMapper;
import com.duck.bankend.mapper.ScriptSceneMapper;
import com.duck.bankend.model.dto.ScriptYamlPreview;
import com.duck.bankend.model.entity.Novel;
import com.duck.bankend.model.entity.NovelChapter;
import com.duck.bankend.model.entity.ScriptCharacterProfile;
import com.duck.bankend.model.entity.ScriptScene;
import com.duck.bankend.service.NovelService;
import com.duck.bankend.service.ScriptYamlExportService;
import com.duck.bankend.util.ScriptCharacterNameNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScriptYamlExportServiceImpl implements ScriptYamlExportService {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String SOURCE_TYPE = "novel";
    private static final String LANGUAGE = "zh-CN";
    private static final String GENERATOR = "AutoScript";
    private static final ZoneId EXPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter METADATA_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NovelService novelService;
    private final NovelChapterMapper chapterMapper;
    private final ScriptSceneMapper sceneMapper;
    private final ScriptCharacterProfileMapper characterProfileMapper;

    @Override
    public ScriptYamlPreview previewYaml(Long novelId) {
        Novel novel = loadNovel(novelId);
        List<NovelChapter> chapters = loadChapters(novelId);
        List<ScriptScene> scenes = loadScenes(novelId);
        if (scenes.isEmpty()) {
            throw new IllegalArgumentException("暂无 AI 场景草稿，请先完成 AI 分析");
        }
        OffsetDateTime exportTime = OffsetDateTime.now(EXPORT_ZONE);
        String fileName = safeFileName(novel.getTitle()) + "-剧本-" + exportTime.format(FILE_TIME_FORMATTER) + ".yaml";
        return new ScriptYamlPreview(fileName, buildYaml(novel, chapters, scenes, exportTime));
    }

    @Override
    public ScriptYamlPreview downloadYaml(Long novelId) {
        return previewYaml(novelId);
    }

    private Novel loadNovel(Long novelId) {
        Novel novel = novelService.getActiveNovel(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("小说不存在或已删除");
        }
        return novel;
    }

    private List<NovelChapter> loadChapters(Long novelId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<NovelChapter>()
                .eq(NovelChapter::getNovelId, novelId)
                .orderByAsc(NovelChapter::getOrderIndex));
    }

    private List<ScriptScene> loadScenes(Long novelId) {
        List<ScriptScene> scenes = sceneMapper.selectList(new LambdaQueryWrapper<ScriptScene>()
                .eq(ScriptScene::getNovelId, novelId)
                .orderByAsc(ScriptScene::getChapterId)
                .orderByAsc(ScriptScene::getChunkId)
                .orderByAsc(ScriptScene::getId));
        scenes.sort(Comparator
                .comparing((ScriptScene scene) -> firstSourceInt(scene.getSourceRefsJson(), "chapter_index", Integer.MAX_VALUE))
                .thenComparing(scene -> firstSourceInt(scene.getSourceRefsJson(), "chunk_index", Integer.MAX_VALUE))
                .thenComparing(ScriptScene::getId));
        return scenes;
    }

    private String buildYaml(Novel novel, List<NovelChapter> chapters, List<ScriptScene> scenes, OffsetDateTime exportTime) {
        Map<String, CharacterDraft> characters = collectCharacters(novel.getId(), scenes);
        StringBuilder yaml = new StringBuilder();
        line(yaml, 0, "schema_version: " + scalar(SCHEMA_VERSION));
        blank(yaml);
        writeMetadata(yaml, novel, exportTime);
        blank(yaml);
        writeSource(yaml, novel, chapters);
        blank(yaml);
        writeCharacters(yaml, characters);
        blank(yaml);
        writeScenes(yaml, scenes, characters);
        return yaml.toString();
    }

    private void writeMetadata(StringBuilder yaml, Novel novel, OffsetDateTime exportTime) {
        line(yaml, 0, "metadata:");
        line(yaml, 1, "title: " + scalar(novel.getTitle()));
        line(yaml, 1, "source_type: " + scalar(SOURCE_TYPE));
        line(yaml, 1, "language: " + scalar(LANGUAGE));
        line(yaml, 1, "generated_at: " + scalar(exportTime.format(METADATA_TIME_FORMATTER)));
        line(yaml, 1, "generator: " + scalar(GENERATOR));
    }

    private void writeSource(StringBuilder yaml, Novel novel, List<NovelChapter> chapters) {
        line(yaml, 0, "source:");
        line(yaml, 1, "novel_id: " + scalar(String.valueOf(novel.getId())));
        line(yaml, 1, "title: " + scalar(novel.getTitle()));
        if (chapters.isEmpty()) {
            line(yaml, 1, "chapters: []");
            return;
        }
        line(yaml, 1, "chapters:");
        for (NovelChapter chapter : chapters) {
            line(yaml, 2, "- index: " + chapter.getOrderIndex());
            line(yaml, 3, "title: " + scalar(chapter.getTitle()));
        }
    }

    private void writeCharacters(StringBuilder yaml, Map<String, CharacterDraft> characters) {
        if (characters.isEmpty()) {
            line(yaml, 0, "characters: []");
            return;
        }
        line(yaml, 0, "characters:");
        for (CharacterDraft character : characters.values()) {
            line(yaml, 1, "- id: " + scalar(character.id()));
            line(yaml, 2, "name: " + scalar(character.name()));
            writeCharacterAliases(yaml, character.aliasesJson());
            line(yaml, 2, "role: " + scalar(character.role()));
            line(yaml, 2, "description: " + scalar(character.description()));
            line(yaml, 2, "first_appearance:");
            line(yaml, 3, "scene_id: " + scalar(character.firstSceneId()));
        }
    }

    private void writeCharacterAliases(StringBuilder yaml, String aliasesJson) {
        ArrayNode aliases = asArray(readJson(aliasesJson));
        if (aliases.isEmpty()) {
            line(yaml, 2, "aliases: []");
            return;
        }
        line(yaml, 2, "aliases:");
        for (JsonNode alias : aliases) {
            if (StringUtils.hasText(alias.asText())) {
                line(yaml, 3, "- " + scalar(alias.asText()));
            }
        }
    }

    private void writeScenes(StringBuilder yaml, List<ScriptScene> scenes, Map<String, CharacterDraft> characters) {
        line(yaml, 0, "scenes:");
        int order = 1;
        Map<String, Integer> titleCounts = new LinkedHashMap<>();
        for (ScriptScene scene : scenes) {
            SourceRef firstSource = firstSourceRef(scene.getSourceRefsJson());
            line(yaml, 1, "- id: " + scalar(scene.getSceneId()));
            line(yaml, 2, "order: " + order++);
            line(yaml, 2, "chapter:");
            line(yaml, 3, "index: " + firstSource.chapterIndex());
            line(yaml, 3, "title: " + scalar(firstSource.chapterTitle()));
            line(yaml, 2, "title: " + scalar(uniqueSceneTitle(scene.getTitle(), titleCounts)));
            line(yaml, 2, "location: " + scalar(scene.getLocation()));
            line(yaml, 2, "time_of_day: " + scalar(scene.getTimeOfDay()));
            writeBlockText(yaml, 2, "summary", scene.getSummary());
            writeSceneCharacters(yaml, scene, characters);
            writeBeats(yaml, scene, characters);
            writeSourceRefs(yaml, scene.getSourceRefsJson());
        }
    }

    private void writeSceneCharacters(StringBuilder yaml, ScriptScene scene, Map<String, CharacterDraft> characters) {
        Set<String> ids = new LinkedHashSet<>();
        for (String name : collectSceneCharacterNames(scene)) {
            CharacterDraft character = characters.get(normalizeNameKey(name));
            if (character != null) {
                ids.add(character.id());
            }
        }
        if (ids.isEmpty()) {
            line(yaml, 2, "characters: []");
            return;
        }
        line(yaml, 2, "characters:");
        for (String id : ids) {
            line(yaml, 3, "- " + scalar(id));
        }
    }

    private void writeBeats(StringBuilder yaml, ScriptScene scene, Map<String, CharacterDraft> characters) {
        ArrayNode beats = exportableBeats(scene);
        if (beats.isEmpty()) {
            line(yaml, 2, "beats: []");
            return;
        }
        line(yaml, 2, "beats:");
        for (JsonNode beat : beats) {
            String type = normalizeBeatType(textOrDefault(beat, "type", "action"));
            line(yaml, 3, "- type: " + scalar(type));
            if ("dialogue".equals(type)) {
                String characterName = dialogueCharacter(beat);
                CharacterDraft character = characters.get(normalizeNameKey(characterName));
                line(yaml, 4, "character_id: " + scalar(character == null ? "" : character.id()));
                line(yaml, 4, "character_name: " + scalar(characterName));
                writeBlockText(yaml, 4, "text", textOrDefault(beat, "text", ""));
            } else {
                writeBlockText(yaml, 4, "text", textOrDefault(beat, "text", ""));
            }
        }
    }

    private ArrayNode exportableBeats(ScriptScene scene) {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode beat : asArray(readJson(scene.getBeatsJson()))) {
            String type = normalizeBeatType(textOrDefault(beat, "type", "action"));
            String text = beatText(beat);
            if (!hasMeaningfulText(text)) {
                continue;
            }
            ObjectNode exported = objectMapper.createObjectNode();
            exported.put("type", type);
            if ("dialogue".equals(type)) {
                String characterName = dialogueCharacter(beat);
                exported.put("character_name", StringUtils.hasText(characterName) ? characterName : "未知");
            }
            exported.put("text", text.trim());
            result.add(exported);
        }
        return result;
    }

    private void writeSourceRefs(StringBuilder yaml, String sourceRefsJson) {
        ArrayNode refs = asArray(readJson(sourceRefsJson));
        if (refs.isEmpty()) {
            line(yaml, 2, "source_refs: []");
            return;
        }
        line(yaml, 2, "source_refs:");
        for (JsonNode ref : refs) {
            line(yaml, 3, "- chapter_index: " + ref.path("chapter_index").asInt(0));
            line(yaml, 4, "chapter_title: " + scalar(ref.path("chapter_title").asText("")));
            line(yaml, 4, "chunk_index: " + ref.path("chunk_index").asInt(0));
            line(yaml, 4, "paragraph_start: " + ref.path("paragraph_start").asInt(0));
            line(yaml, 4, "paragraph_end: " + ref.path("paragraph_end").asInt(0));
        }
    }

    private Map<String, CharacterDraft> collectCharacters(Long novelId, List<ScriptScene> scenes) {
        Map<String, CharacterDraft> characters = new LinkedHashMap<>();
        Map<String, Integer> idCounts = new LinkedHashMap<>();
        for (ScriptScene scene : scenes) {
            for (String name : collectSceneCharacterNames(scene)) {
                addCharacter(characters, idCounts, name, scene.getSceneId());
            }
        }
        applyCharacterProfiles(novelId, characters);
        return characters;
    }

    private void applyCharacterProfiles(Long novelId, Map<String, CharacterDraft> characters) {
        List<ScriptCharacterProfile> profiles = characterProfileMapper.selectList(new LambdaQueryWrapper<ScriptCharacterProfile>()
                .eq(ScriptCharacterProfile::getNovelId, novelId));
        for (ScriptCharacterProfile profile : profiles) {
            String key = StringUtils.hasText(profile.getCharacterKey())
                    ? profile.getCharacterKey()
                    : normalizeNameKey(profile.getName());
            CharacterDraft existing = characters.get(key);
            if (existing == null) {
                continue;
            }
            characters.put(key, existing.withProfile(profile));
        }
    }

    private List<String> collectSceneCharacterNames(ScriptScene scene) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode character : asArray(readJson(scene.getCharactersJson()))) {
            String name = ScriptCharacterNameNormalizer.displayName(character.isTextual() ? character.asText() : character.path("name").asText(""));
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        for (JsonNode beat : asArray(readJson(scene.getBeatsJson()))) {
            if ("dialogue".equals(beat.path("type").asText())) {
                String characterName = dialogueCharacter(beat);
                if (StringUtils.hasText(characterName)) {
                    names.add(characterName);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private void addCharacter(Map<String, CharacterDraft> characters, Map<String, Integer> idCounts, String name, String sceneId) {
        name = ScriptCharacterNameNormalizer.displayName(name);
        String key = normalizeNameKey(name);
        if (!StringUtils.hasText(key) || characters.containsKey(key)) {
            return;
        }
        String baseId = "character-" + normalizeIdPart(name);
        int count = idCounts.getOrDefault(baseId, 0) + 1;
        idCounts.put(baseId, count);
        String id = count == 1 ? baseId : baseId + "-" + count;
        characters.put(key, new CharacterDraft(id, name.trim(), sceneId, "[]", "", ""));
    }

    private String dialogueCharacter(JsonNode beat) {
        String character = beat.path("character_name").asText(null);
        if (StringUtils.hasText(character)) {
            return ScriptCharacterNameNormalizer.displayName(character);
        }
        character = beat.path("character").asText(null);
        return ScriptCharacterNameNormalizer.displayName(StringUtils.hasText(character) ? character : beat.path("character_id").asText(""));
    }

    private SourceRef firstSourceRef(String sourceRefsJson) {
        JsonNode first = asArray(readJson(sourceRefsJson)).path(0);
        return new SourceRef(
                first.path("chapter_index").asInt(0),
                first.path("chapter_title").asText(""),
                first.path("chunk_index").asInt(0),
                first.path("paragraph_start").asInt(0),
                first.path("paragraph_end").asInt(0)
        );
    }

    private int firstSourceInt(String sourceRefsJson, String field, int defaultValue) {
        JsonNode first = asArray(readJson(sourceRefsJson)).path(0);
        return first.has(field) ? first.path(field).asInt(defaultValue) : defaultValue;
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

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText(defaultValue);
    }

    private String beatText(JsonNode beat) {
        String text = textOrDefault(beat, "text", "");
        if (StringUtils.hasText(text)) {
            return text;
        }
        text = textOrDefault(beat, "description", "");
        if (StringUtils.hasText(text)) {
            return text;
        }
        return textOrDefault(beat, "content", "");
    }

    private boolean hasMeaningfulText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return StringUtils.hasText(value.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】—…-]+", ""));
    }

    private String uniqueSceneTitle(String title, Map<String, Integer> titleCounts) {
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : "未命名场景";
        String key = normalizedTitle.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】]+", "").toLowerCase();
        int count = titleCounts.getOrDefault(key, 0) + 1;
        titleCounts.put(key, count);
        return count == 1 ? normalizedTitle : normalizedTitle + "（" + count + "）";
    }

    private String normalizeNameKey(String name) {
        return ScriptCharacterNameNormalizer.key(name);
    }

    private String normalizeBeatType(String type) {
        if ("dialogue".equals(type) || "transition".equals(type)) {
            return type;
        }
        return "action";
    }

    private String normalizeIdPart(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String normalized = Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFKD)
                .replaceAll("[\\p{M}]+", "")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return StringUtils.hasText(normalized) ? normalized : "unknown";
    }

    private String safeFileName(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "剧本";
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|]+", "-").replaceAll("\\s+", " ");
        return StringUtils.hasText(normalized) ? normalized : "剧本";
    }

    private void writeBlockText(StringBuilder yaml, int indent, String key, String value) {
        String text = value == null ? "" : value;
        if (!text.contains("\n") && text.length() <= 80) {
            line(yaml, indent, key + ": " + scalar(text));
            return;
        }
        line(yaml, indent, key + ": |-");
        if (!StringUtils.hasText(text)) {
            line(yaml, indent + 1, "");
            return;
        }
        for (String part : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            line(yaml, indent + 1, part);
        }
    }

    private String scalar(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void line(StringBuilder yaml, int indent, String text) {
        yaml.append("  ".repeat(Math.max(0, indent))).append(text).append('\n');
    }

    private void blank(StringBuilder yaml) {
        yaml.append('\n');
    }

    private record CharacterDraft(String id, String name, String firstSceneId,
                                  String aliasesJson, String role, String description) {

        private CharacterDraft withProfile(ScriptCharacterProfile profile) {
            return new CharacterDraft(id, name, firstSceneId,
                    StringUtils.hasText(profile.getAliasesJson()) ? profile.getAliasesJson() : "[]",
                    StringUtils.hasText(profile.getRole()) ? profile.getRole() : "",
                    StringUtils.hasText(profile.getDescription()) ? profile.getDescription() : "");
        }
    }

    private record SourceRef(int chapterIndex, String chapterTitle, int chunkIndex, int paragraphStart, int paragraphEnd) {
    }
}
