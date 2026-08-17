package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.ZhimouAppPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ZhimouAppPackageMapper extends BaseMapper<ZhimouAppPackage> {

    @Update("DELETE FROM zhimou_app_package")
    @Options(useGeneratedKeys = false)
    void deleteAll();
}