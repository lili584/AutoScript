package com.duck.bankend.model.dto;

import com.duck.bankend.model.entity.ScriptScene;
import lombok.Data;

import java.util.List;

@Data
public class ScriptSceneView {

    private Long id;
    private Long novelId;
    private Long chapterId;
    private Long chunkId;
    private String sceneId;
    private String title;
    private String location;
    private String timeOfDay;
    private String summary;
    private List<String> characters;
    private Integer beatsCount;
    private String charactersJson;
    private String beatsJson;
    private String sourceRefsJson;

    public static ScriptSceneView from(ScriptScene scene, List<String> characters, int beatsCount) {
        ScriptSceneView view = new ScriptSceneView();
        view.setId(scene.getId());
        view.setNovelId(scene.getNovelId());
        view.setChapterId(scene.getChapterId());
        view.setChunkId(scene.getChunkId());
        view.setSceneId(scene.getSceneId());
        view.setTitle(scene.getTitle());
        view.setLocation(scene.getLocation());
        view.setTimeOfDay(scene.getTimeOfDay());
        view.setSummary(scene.getSummary());
        view.setCharacters(characters);
        view.setBeatsCount(beatsCount);
        view.setCharactersJson(scene.getCharactersJson());
        view.setBeatsJson(scene.getBeatsJson());
        view.setSourceRefsJson(scene.getSourceRefsJson());
        return view;
    }
}
