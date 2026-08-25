package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.SelfModelImageDetection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自有模型图片识别结果表 Mapper
 * 对应 self_model_image_detection 表
 */
@Mapper
public interface SelfModelImageDetectionMapper extends BaseMapper<SelfModelImageDetection> {
}
