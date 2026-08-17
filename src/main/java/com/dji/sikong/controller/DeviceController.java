package com.dji.sikong.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sikong.common.R;
import com.dji.sikong.dto.AircraftVO;
import com.dji.sikong.dto.DeviceRealtimeVO;
import com.dji.sikong.dto.DeviceTopologyVO;
import com.dji.sikong.entity.Device;
import com.dji.sikong.entity.DeviceRealtimeStatus;
import com.dji.sikong.service.DeviceRealtimeStatusService;
import com.dji.sikong.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/device")
@CrossOrigin(origins = "*")
public class DeviceController {

    @Resource
    private DeviceService deviceService;

    @Resource
    private DeviceRealtimeStatusService realtimeStatusService;

    // ====================================================
    // 需求1：实时信息查询
    // ====================================================

    /**
     * 查询单个设备的实时信息
     * 包含：在线状态、经纬度、剩余电量、风速、电池温度、飞机状态、距离Home点距离、GPS搜星数量
     */
    @GetMapping("/realtime/{deviceSn}")
    public R<DeviceRealtimeVO> getRealtimeInfo(@PathVariable String deviceSn) {
        DeviceRealtimeVO vo = deviceService.getRealtimeInfo(deviceSn);
        return vo == null ? R.fail("设备不存在") : R.ok(vo);
    }

    /**
     * 批量查询实时信息，不传参返回所有设备
     */
    @GetMapping("/realtime/list")
    public R<List<DeviceRealtimeVO>> getRealtimeInfoList(
            @RequestParam(required = false) String deviceSns) {
        List<String> sns = null;
        if (StrUtil.isNotBlank(deviceSns)) {
            sns = Arrays.stream(deviceSns.split(",")).collect(Collectors.toList());
        }
        return R.ok(deviceService.getRealtimeInfoList(sns));
    }

    // ====================================================
    // 需求5：设备拓扑信息(带飞机型号+各镜头型号)
    // ====================================================

    /**
     * 获取完整的设备拓扑信息
     * 网关(机场/遥控器) -> 飞机 -> 多个镜头(广角/变焦/红外)
     */
    @GetMapping("/topology")
    public R<List<DeviceTopologyVO>> getDeviceTopology() {
        return R.ok(deviceService.getDeviceTopology());
    }

    /**
     * 获取单个飞机的详细信息(含镜头列表)
     */
    @GetMapping("/aircraft/{aircraftSn}")
    public R<AircraftVO> getAircraftDetail(@PathVariable String aircraftSn) {
        Device ac = deviceService.getBySn(aircraftSn);
        if (ac == null) return R.fail("飞机不存在");

        AircraftVO vo = new AircraftVO();
        vo.setAircraftSn(ac.getSn());
        vo.setAircraftName(ac.getDeviceName() == null ? ac.getSn() : ac.getDeviceName());
        vo.setAircraftModel(ac.getDeviceModel());
        vo.setModelKey(ac.getModelKey());
        vo.setOnlineStatus(ac.getOnlineStatus() == null ? 0 : ac.getOnlineStatus());
        vo.setPairedStatus(ac.getPairedStatus() == null ? 0 : ac.getPairedStatus());

        DeviceRealtimeStatus rs = realtimeStatusService.getByDeviceSn(aircraftSn);
        if (rs != null) vo.setDroneInDock(rs.getDroneInDock());

        com.dji.sikong.dto.DeviceRealtimeVO realtime = deviceService.getRealtimeInfo(aircraftSn);
        vo.setCameras(realtime != null ? realtime.getCameras() : null);
        return R.ok(vo);
    }

    // ====================================================
    // 基础管理接口
    // ====================================================

    @GetMapping("/list")
    public R<List<Device>> listDevices(
            @RequestParam(required = false) Integer deviceType,
            @RequestParam(required = false) Integer onlineStatus) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>().eq(Device::getDeleted, 0);
        if (deviceType != null) wrapper.eq(Device::getDeviceType, deviceType);
        if (onlineStatus != null) wrapper.eq(Device::getOnlineStatus, onlineStatus);
        wrapper.orderByDesc(Device::getUpdateTime);
        return R.ok(deviceService.list(wrapper));
    }

    @GetMapping("/page")
    public R<Page<Device>> pageDevices(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer deviceType,
            @RequestParam(required = false) Integer onlineStatus,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>().eq(Device::getDeleted, 0);
        if (deviceType != null) wrapper.eq(Device::getDeviceType, deviceType);
        if (onlineStatus != null) wrapper.eq(Device::getOnlineStatus, onlineStatus);
        if (StrUtil.isNotBlank(keyword)) wrapper.and(w -> w.like(Device::getSn, keyword)
                .or().like(Device::getDeviceName, keyword));
        wrapper.orderByDesc(Device::getUpdateTime);
        return R.ok(deviceService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @GetMapping("/{id}")
    public R<Device> getById(@PathVariable Long id) {
        return R.ok(deviceService.getById(id));
    }

    @PostMapping
    public R<Boolean> save(@RequestBody Device device) {
        return R.ok(deviceService.saveOrUpdate(device));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(deviceService.removeById(id));
    }
}
