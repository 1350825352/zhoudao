package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.OrganizationProject;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationProjectMapper extends BaseMapper<OrganizationProject> {

    @Delete("DELETE FROM organization_project WHERE project_uuid = #{puuid}")
    void physicalDeleteByProjectUuid(@Param("puuid") String puuid);
}
