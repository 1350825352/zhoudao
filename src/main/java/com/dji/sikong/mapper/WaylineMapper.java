package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.Wayline;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WaylineMapper extends BaseMapper<Wayline> {

    @Delete("DELETE FROM wayline WHERE wayline_uuid = #{wuuid}")
    void physicalDeleteByWaylineUuid(@Param("wuuid") String wuuid);
}
