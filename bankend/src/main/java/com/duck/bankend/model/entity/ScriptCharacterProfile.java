package com.duck.bankend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("script_character_profiles")
public class ScriptCharacterProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private String characterKey;

    private String name;

    private String aliasesJson;

    private String role;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
