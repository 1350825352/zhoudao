package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.entity.DeviceRealtimeStatus;
import com.fasterxml.jackson.databind.JsonNode;

public interface DeviceRealtimeStatusService extends IService<DeviceRealtimeStatus> {

    void saveOrUpdateFromOsd(String deviceSn, String gatewaySn, JsonNode osdNode);

    DeviceRealtimeStatus getByDeviceSn(String deviceSn);

    /**
     * 统一mode_code开关码流逻辑(OSD通道与DRC通道共用,确保降落观察期防抖策略一致):
     *   mode_code ∈ FLYING_MODES → 立即生效清锁+开码流
     *   mode_code ∉ FLYING_MODES → 连续5秒一致才加锁+关码流
     */
    void handleModeCode(String aircraftSn, String gatewaySn, int modeCode);
}
