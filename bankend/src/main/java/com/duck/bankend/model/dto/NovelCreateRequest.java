package com.duck.bankend.model.dto;

import lombok.Data;

@Data
public class NovelCreateRequest {

    private String title;

    private String description;

    private String content;
}
