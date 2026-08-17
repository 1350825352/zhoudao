package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.common.DeviceTypeConstants;
import com.dji.sikong.common.RedisKeyConstants;
import com.dji.sikong.dto.*;
import com.dji.sikong.entity.AircraftCamera;
import com.dji.sikong.entity.Device;
import com.dji.sikong.config.DjiConfig;
import com.dji.sikong.entity.DeviceRealtimeStatus;
import com.dji.sikong.mapper.AircraftCameraMapper;
import com.dji.sikong.mapper.DeviceMapper;
import com.dji.sikong.service.DeviceRealtimeStatusService;
import com.dji.sikong.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private AircraftCameraMapper cameraMapper;

    @Resource
    private DeviceRealtimeStatusService realtimeStatusService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DjiConfig djiConfig;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Device getBySn(String sn) {
        if (StrUtil.isBlank(sn)) return null;
        // 先查Redis缓存
        String key = RedisKeyConstants.DEVICE_INFO_KEY + sn;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cached)) {
            try { return JSONUtil.toBean(cached, Device.class); } catch (Exception ignore) {}
        }
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getSn, sn));
        if (device != null) {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(device),
                    RedisKeyConstants.DEFAULT_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        }
        return device;
    }

    @Override
    public void saveOrUpdateDevice(Device device) {
        if (device == null || StrUtil.isBlank(device.getSn())) return;
        Device exist = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getSn, device.getSn()));
        if (exist == null) {
            if (device.getDeleted() == null) device.setDeleted(0);
            if (device.getCreateTime() == null) device.setCreateTime(java.time.LocalDateTime.now());
            if (device.getUpdateTime() == null) device.setUpdateTime(java.time.LocalDateTime.now());
            deviceMapper.insert(device);
        } else {
            device.setId(exist.getId());
            device.setUpdateTime(java.time.LocalDateTime.now());
            deviceMapper.updateById(device);
        }
        // 清理缓存
        stringRedisTemplate.delete(RedisKeyConstants.DEVICE_INFO_KEY + device.getSn());
    }

    @Override
    public void saveCameras(String aircraftSn, Object cameraList) {
        if (StrUtil.isBlank(aircraftSn) || cameraList == null) return;
        try {
            JsonNode cameras;
            if (cameraList instanceof JsonNode) {
                cameras = (JsonNode) cameraList;
            } else if (cameraList instanceof String) {
                    cameras = new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) cameraList);
                } else {
                    return;
                }
            if (!cameras.isArray()) return;

                for (JsonNode node : cameras) {
                    String cameraIndex = node.has("camera_index") ? node.get("camera_index").asText()
                            : (node.has("index") ? node.get("index").asText()
                            : (node.has("payload_index") ? node.get("payload_index").asText() : null));
                    if (StrUtil.isBlank(cameraIndex)) continue;

                    AircraftCamera exist = cameraMapper.selectOne(
                            com.baomidou.mybatisplus.core.toolkit.Wrappers.<AircraftCamera>lambdaQuery()
                                    .eq(AircraftCamera::getAircraftSn, aircraftSn)
                                    .eq(AircraftCamera::getCameraIndex, cameraIndex));
                    AircraftCamera cam = exist == null ? new AircraftCamera() : exist;
                    cam.setAircraftSn(aircraftSn);
                    cam.setCameraIndex(cameraIndex);
                    if (node.has("camera_model_key")) cam.setLensType(node.get("camera_model_key").asText());
                    if (node.has("lens_type")) cam.setLensType(node.get("lens_type").asText());
                    if (node.has("pixel")) cam.setPixel(node.get("pixel").asText());
                    if (node.has("focal_length")) cam.setFocalLength(node.get("focal_length").asText());
                    if (node.has("camera_name")) cam.setCameraModel(node.get("camera_name").asText());
                    if (node.has("camera_type")) cam.setCameraType(node.get("camera_type").asInt());

                    if (exist == null) {
                        if (cam.getDeleted() == null) cam.setDeleted(0);
                        if (cam.getCreateTime() == null) cam.setCreateTime(java.time.LocalDateTime.now());
                        if (cam.getUpdateTime() == null) cam.setUpdateTime(java.time.LocalDateTime.now());
                        cameraMapper.insert(cam);
                    } else {
                        cam.setId(exist.getId());
                        cam.setUpdateTime(java.time.LocalDateTime.now());
                        cameraMapper.updateById(cam);
                    }
                }
        } catch (Exception e) {
            log.debug("保存相机信息异常 aircraftSn={}", aircraftSn, e);
        }
    }

    @Override
    public List<DeviceTopologyVO> getDeviceTopology() {
        String projectUuid = djiConfig.getDefaultProjectUuid();

        // 1. 查指定项目下的网关设备(机场/遥控器)
        List<Device> gateways = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .in(Device::getDeviceType, DeviceTypeConstants.GATEWAY_DOCK, DeviceTypeConstants.GATEWAY_REMOTE)
                        .eq(Device::getDeleted, 0)
                        .eq(StrUtil.isNotBlank(projectUuid), Device::getProjectUuid, projectUuid));

        List<DeviceTopologyVO> result = new ArrayList<>();
        for (Device gw : gateways) {
            DeviceTopologyVO vo = new DeviceTopologyVO();
            vo.setGatewaySn(gw.getSn());
            vo.setGatewayName(gw.getDeviceName() == null ? gw.getSn() : gw.getDeviceName());
            vo.setGatewayType(gw.getDeviceType());
            vo.setGatewayModel(gw.getDeviceModel());
            vo.setOnlineStatus(gw.getOnlineStatus() == null ? 0 : gw.getOnlineStatus());

            // 查该网关下属于指定项目的飞机
            List<Device> aircrafts = deviceMapper.selectList(
                    new LambdaQueryWrapper<Device>()
                            .eq(Device::getGatewaySn, gw.getSn())
                            .eq(Device::getDeviceType, DeviceTypeConstants.AIRCRAFT)
                            .eq(Device::getDeleted, 0)
                            .eq(StrUtil.isNotBlank(projectUuid), Device::getProjectUuid, projectUuid));
            List<AircraftVO> aircraftVOs = new ArrayList<>();
            for (Device ac : aircrafts) {
                AircraftVO acVo = new AircraftVO();
                acVo.setAircraftSn(ac.getSn());
                acVo.setAircraftName(ac.getDeviceName() == null ? ac.getSn() : ac.getDeviceName());
                acVo.setAircraftModel(ac.getDeviceModel());
                acVo.setModelKey(ac.getModelKey());
                acVo.setOnlineStatus(ac.getOnlineStatus() == null ? 0 : ac.getOnlineStatus());
                acVo.setPairedStatus(ac.getPairedStatus() == null ? 0 : ac.getPairedStatus());
                // 获取在机场内状态
                DeviceRealtimeStatus rs = realtimeStatusService.getByDeviceSn(ac.getSn());
                if (rs != null) acVo.setDroneInDock(rs.getDroneInDock());
                // 获取镜头
                List<AircraftCamera> cameras = cameraMapper.selectList(
                        new LambdaQueryWrapper<AircraftCamera>()
                                .eq(AircraftCamera::getAircraftSn, ac.getSn())
                                .eq(AircraftCamera::getDeleted, 0));
                acVo.setCameras(cameras.stream().map(this::toCameraVO).collect(Collectors.toList()));
                aircraftVOs.add(acVo);
            }
            vo.setAircrafts(aircraftVOs);
            result.add(vo);
        }
        return result;
    }

    @Override
    public DeviceRealtimeVO getRealtimeInfo(String deviceSn) {
        Device device = getBySn(deviceSn);
        if (device == null) return null;
        DeviceRealtimeStatus rs = realtimeStatusService.getByDeviceSn(deviceSn);
        // 如果直接查不到，尝试用gatewaySn(机场SN)查
        if (rs == null && StrUtil.isNotBlank(device.getGatewaySn())) {
            rs = realtimeStatusService.getByDeviceSn(device.getGatewaySn());
        }
        return buildRealtimeVO(device, rs);
    }

    @Override
    public List<DeviceRealtimeVO> getRealtimeInfoList(List<String> deviceSns) {
        List<DeviceRealtimeVO> result = new ArrayList<>();
        if (deviceSns == null || deviceSns.isEmpty()) {
            List<Device> all = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                    .in(Device::getDeviceType, DeviceTypeConstants.AIRCRAFT, DeviceTypeConstants.GATEWAY_DOCK)
                    .eq(Device::getDeleted, 0));
            for (Device d : all) {
                DeviceRealtimeStatus rs = realtimeStatusService.getByDeviceSn(d.getSn());
                result.add(buildRealtimeVO(d, rs));
            }
        } else {
            for (String sn : deviceSns) {
                Device d = getBySn(sn);
                if (d == null) continue;
                DeviceRealtimeStatus rs = realtimeStatusService.getByDeviceSn(sn);
                result.add(buildRealtimeVO(d, rs));
            }
        }
        return result;
    }

    private DeviceRealtimeVO buildRealtimeVO(Device device, DeviceRealtimeStatus rs) {
        DeviceRealtimeVO vo = new DeviceRealtimeVO();
        vo.setDeviceSn(device.getSn());
        vo.setDeviceName(device.getDeviceName() == null ? device.getSn() : device.getDeviceName());
        vo.setDeviceModel(device.getDeviceModel());
        vo.setDeviceType(device.getDeviceType());
        vo.setGatewaySn(device.getGatewaySn());

        // 在线状态: 缓存 > 实时表 > 设备表
        Integer online = null;
        String onlineStr = stringRedisTemplate.opsForValue().get(RedisKeyConstants.DEVICE_ONLINE_KEY + device.getSn());
        if (StrUtil.isNotBlank(onlineStr)) online = Integer.parseInt(onlineStr);
        if (online == null) online = device.getOnlineStatus() == null ? 0 : device.getOnlineStatus();
        vo.setOnlineStatus(online);

        if (rs != null) {
            vo.setLatitude(rs.getLatitude());
            vo.setLongitude(rs.getLongitude());
            vo.setHeight(rs.getHeight());
            vo.setAltitude(rs.getAltitude());
            vo.setElevation(rs.getElevation());
            vo.setVerticalSpeed(rs.getVerticalSpeed());
            vo.setHorizontalSpeed(rs.getHorizontalSpeed());
            vo.setHomeLatitude(rs.getHomeLatitude());
            vo.setHomeLongitude(rs.getHomeLongitude());
            vo.setDistanceToHome(rs.getDistanceToHome());
            vo.setBatteryPercent(rs.getBatteryPercent());
            vo.setBatteryTemperature(rs.getBatteryTemperature());
            vo.setWindSpeed(rs.getWindSpeed());
            vo.setEnvironmentTemperature(rs.getEnvironmentTemperature());
            vo.setHumidity(rs.getHumidity());
            vo.setRainfall(rs.getRainfall());
            vo.setGpsSatelliteCount(rs.getGpsSatelliteCount());
            vo.setRtkSatelliteCount(rs.getRtkSatelliteCount());
            vo.setRtkFixed(rs.getRtkFixed());
            vo.setFlightStatus(rs.getFlightStatus());
            vo.setDeviceMode(rs.getDeviceMode());
            vo.setDroneInDock(rs.getDroneInDock());
            vo.setUpdateTime(rs.getUpdateTime() == null ? null : rs.getUpdateTime().format(FORMATTER));
        }

        // 如果是飞机，带镜头
        if (Objects.equals(DeviceTypeConstants.AIRCRAFT, device.getDeviceType())) {
            List<AircraftCamera> cameras = cameraMapper.selectList(
                    new LambdaQueryWrapper<AircraftCamera>()
                            .eq(AircraftCamera::getAircraftSn, device.getSn())
                            .eq(AircraftCamera::getDeleted, 0));
            vo.setCameras(cameras.stream().map(this::toCameraVO).collect(Collectors.toList()));
        }
        return vo;
    }

    private AircraftCameraVO toCameraVO(AircraftCamera entity) {
        AircraftCameraVO vo = new AircraftCameraVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    @Override
    public String getAircraftSnByGatewaySn(String gatewaySn) {
        if (StrUtil.isBlank(gatewaySn)) return null;
        Device aircraft = baseMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getGatewaySn, gatewaySn)
                .ne(Device::getSn, gatewaySn)
                .eq(Device::getDeleted, 0)
                .last(" LIMIT 1"));
        return aircraft != null ? aircraft.getSn() : null;
    }
}
