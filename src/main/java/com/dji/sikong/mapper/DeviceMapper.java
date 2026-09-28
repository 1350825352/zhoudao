package com.dji.sikong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dji.sikong.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /** 物理清空 device 表（TRUNCATE，重置自增 ID，不可恢复） */
    @Update("TRUNCATE TABLE device")
    void truncateTable();
}
