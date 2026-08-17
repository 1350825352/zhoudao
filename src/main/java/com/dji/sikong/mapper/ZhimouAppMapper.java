package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.ZhimouApp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ZhimouAppMapper extends BaseMapper<ZhimouApp> {

    List<ZhimouApp> selectByZapkId(String zapkId);

    @Update("DELETE FROM zhimou_app")
    @Options(useGeneratedKeys = false)
    void deleteAll();
}