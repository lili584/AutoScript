package com.duck.bankend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("script_chapter_states")
public class ScriptChapterState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private Long chapterId;

    private Long chunkId;

    private Integer chapterIndex;

    private Integer chunkIndex;

    private String stateJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
