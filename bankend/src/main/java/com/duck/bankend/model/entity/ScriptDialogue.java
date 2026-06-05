package com.duck.bankend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("script_dialogues")
public class ScriptDialogue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneDbId;

    private String characterName;

    private String text;

    private LocalDateTime createdAt;
}
