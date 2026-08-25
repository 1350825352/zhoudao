package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.ZhimouImageDetection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知眸图片识别结果表 Mapper
 * 对应 zhimou_image_detection 表
 */
@Mapper
public interface ZhimouImageDetectionMapper extends BaseMapper<ZhimouImageDetection> {
}
