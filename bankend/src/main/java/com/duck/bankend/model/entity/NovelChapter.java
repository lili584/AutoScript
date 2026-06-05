package com.duck.bankend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("novel_chapters")
public class NovelChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private String title;

    private Integer orderIndex;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
