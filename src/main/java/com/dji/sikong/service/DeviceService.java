package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.dto.DeviceRealtimeVO;
import com.dji.sikong.dto.DeviceTopologyVO;
import com.dji.sikong.entity.Device;

import java.util.List;

public interface DeviceService extends IService<Device> {

    Device getBySn(String sn);

    void saveOrUpdateDevice(Device device);

    void saveCameras(String aircraftSn, Object cameraList);

    List<DeviceTopologyVO> getDeviceTopology();

    DeviceRealtimeVO getRealtimeInfo(String deviceSn);

    List<DeviceRealtimeVO> getRealtimeInfoList(List<String> deviceSns);

    /** 通过机场SN(gatewaySn)找到配对的飞机SN */
    String getAircraftSnByGatewaySn(String gatewaySn);
}
