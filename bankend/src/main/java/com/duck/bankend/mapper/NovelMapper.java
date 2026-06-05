package com.duck.bankend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.duck.bankend.model.entity.Novel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NovelMapper extends BaseMapper<Novel> {
}
