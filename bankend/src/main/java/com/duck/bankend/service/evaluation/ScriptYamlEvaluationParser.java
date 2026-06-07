package com.duck.bankend.service.evaluation;

import com.duck.bankend.model.evaluation.ScriptYamlData;
import com.duck.bankend.model.evaluation.YamlBeatData;
import com.duck.bankend.model.evaluation.YamlCharacterData;
import com.duck.bankend.model.evaluation.YamlSceneData;
import com.duck.bankend.model.evaluation.YamlSourceRefData;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ScriptYamlEvaluationParser {

    public ScriptYamlData parse(String yamlText) {
        if (!StringUtils.hasText(yamlText)) {
            throw new IllegalArgumentException("YAML 文件内容不能为空");
        }
        Object rootObject;
        try {
            rootObject = new Yaml().load(yamlText);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("YAML 格式解析失败: " + exception.getMessage());
        }
        Map<String, Object> root = asMap(rootObject);
        if (root.isEmpty()) {
            throw new IllegalArgumentException("YAML 文件内容不能为空");
        }
        List<YamlSceneData> scenes = parseScenes(asList(root.get("scenes")));
        if (scenes.isEmpty()) {
            throw new IllegalArgumentException("YAML 缺少 scenes 或 scenes 为空");
        }
        return new ScriptYamlData(
                stringValue(root.get("schema_version")),
                metadataTitle(root),
                parseCharacters(asList(root.get("characters"))),
                scenes
        );
    }

    private List<YamlCharacterData> parseCharacters(List<Object> nodes) {
        List<YamlCharacterData> characters = new ArrayList<>();
        for (Object node : nodes) {
            Map<String, Object> map = asMap(node);
            String id = stringValue(map.get("id"));
            String name = stringValue(map.get("name"));
            if (StringUtils.hasText(id) || StringUtils.hasText(name)) {
                characters.add(new YamlCharacterData(id, name, stringValue(map.get("role")), stringValue(map.get("description"))));
            }
        }
        return characters;
    }

    private List<YamlSceneData> parseScenes(List<Object> nodes) {
        List<YamlSceneData> scenes = new ArrayList<>();
        for (Object node : nodes) {
            Map<String, Object> map = asMap(node);
            String id = stringValue(map.get("id"));
            Map<String, Object> chapter = asMap(map.get("chapter"));
            int chapterIndex = intValue(chapter.get("index"));
            String chapterTitle = stringValue(chapter.get("title"));
            List<String> characters = asList(map.get("characters")).stream().map(this::stringValue).filter(StringUtils::hasText).toList();
            List<YamlSourceRefData> sourceRefs = parseSourceRefs(asList(map.get("source_refs")));
            if (chapterIndex == 0 && !sourceRefs.isEmpty()) {
                chapterIndex = sourceRefs.get(0).chapterIndex();
                chapterTitle = sourceRefs.get(0).chapterTitle();
            }
            List<YamlBeatData> beats = parseBeats(asList(map.get("beats")), id, chapterIndex);
            scenes.add(new YamlSceneData(
                    id,
                    intValue(map.get("order")),
                    chapterIndex,
                    chapterTitle,
                    stringValue(map.get("title")),
                    stringValue(map.get("location")),
                    stringValue(map.get("time_of_day")),
                    stringValue(map.get("summary")),
                    characters,
                    beats,
                    sourceRefs
            ));
        }
        return scenes;
    }

    private List<YamlBeatData> parseBeats(List<Object> nodes, String sceneId, int chapterIndex) {
        List<YamlBeatData> beats = new ArrayList<>();
        for (Object node : nodes) {
            Map<String, Object> map = asMap(node);
            String type = stringValue(map.get("type"));
            String text = stringValue(map.get("text"));
            if (StringUtils.hasText(type) || StringUtils.hasText(text)) {
                beats.add(new YamlBeatData(type, text, stringValue(map.get("character_id")), stringValue(map.get("character_name")), sceneId, chapterIndex));
            }
        }
        return beats;
    }

    private List<YamlSourceRefData> parseSourceRefs(List<Object> nodes) {
        List<YamlSourceRefData> refs = new ArrayList<>();
        for (Object node : nodes) {
            Map<String, Object> map = asMap(node);
            refs.add(new YamlSourceRefData(
                    intValue(map.get("chapter_index")),
                    stringValue(map.get("chapter_title")),
                    intValue(map.get("chunk_index")),
                    intValue(map.get("paragraph_start")),
                    intValue(map.get("paragraph_end"))
            ));
        }
        return refs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private String metadataTitle(Map<String, Object> root) {
        Map<String, Object> metadata = asMap(root.get("metadata"));
        return stringValue(metadata.get("title"));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
