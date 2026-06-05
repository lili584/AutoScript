package com.duck.bankend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("novel_chunks")
public class NovelChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private Long chapterId;

    private Integer chapterIndex;

    private Integer chunkIndex;

    private String content;

    private String context;

    private Integer paragraphStart;

    private Integer paragraphEnd;

    private Integer charCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
