package com.duck.bankend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("script_scenes")
public class ScriptScene {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private Long chapterId;

    private Long chunkId;

    private String sceneId;

    private String title;

    private String location;

    private String timeOfDay;

    private String summary;

    private String charactersJson;

    private String beatsJson;

    private String sourceRefsJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
