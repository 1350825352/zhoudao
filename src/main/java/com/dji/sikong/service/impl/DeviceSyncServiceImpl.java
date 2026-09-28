package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dji.sikong.common.DeviceTypeConstants;
import com.dji.sikong.config.DjiOpenApiProperties;
import com.dji.sikong.entity.Device;
import com.dji.sikong.mapper.DeviceMapper;
import com.dji.sikong.openapi.DjiOpenApiClient;
import com.dji.sikong.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DeviceSyncServiceImpl {

    @Resource
    private DjiOpenApiClient openApiClient;

    @Resource
    private DeviceService deviceService;

    @Resource
    private DjiOpenApiProperties openApiProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DeviceMapper deviceMapper;

    @PostConstruct
    public void init() {
        syncProjectDevicesToCache();
        log.info("项目设备缓存初始化完成（启动时立即同步）");
    }

    @Scheduled(fixedRate = 300000)
    public void syncDeviceNamesFromOpenApi() {
        try {
            JsonNode convertersNode = openApiClient.listStreamConverters(null);
            if (convertersNode == null || !convertersNode.has("data")) {
                log.debug("OpenAPI码流转发器列表为空");
                return;
            }

            JsonNode dataNode = convertersNode.get("data");
            if (!dataNode.has("list")) {
                log.debug("OpenAPI码流转发器列表无list字段");
                return;
            }

            Map<String, String> callsignMap = new HashMap<>();
            for (JsonNode converter : dataNode.get("list")) {
                String sn = converter.has("sn") ? converter.get("sn").asText() : "";
                String callsign = converter.has("device_callsign") ? converter.get("device_callsign").asText() : "";
                if (StrUtil.isNotBlank(sn) && StrUtil.isNotBlank(callsign)) {
                    callsignMap.put(sn, callsign);
                }
            }

            if (callsignMap.isEmpty()) {
                log.debug("未获取到设备别名");
                return;
            }

            int updated = 0;
            for (Map.Entry<String, String> entry : callsignMap.entrySet()) {
                Device device = deviceService.getBySn(entry.getKey());
                if (device != null) {
                    String oldName = device.getDeviceName();
                    String newName = entry.getValue();
                    if (!newName.equals(oldName)) {
                        device.setDeviceName(newName);
                        deviceService.saveOrUpdateDevice(device);
                        updated++;
                        log.info("设备名称更新: sn={}, oldName={}, newName={}", entry.getKey(), oldName, newName);
                    }
                }
            }

            if (updated > 0) {
                log.info("设备名称同步完成，更新{}条记录", updated);
            }
        } catch (Exception e) {
            log.error("设备名称同步失败", e);
        }
    }

    @Scheduled(fixedRate = 300000)
    public void syncProjectDevicesToCache() {
        String defaultProjectUuid = openApiProperties.getDefaultProjectUuid();
        if (StrUtil.isBlank(defaultProjectUuid)) {
            log.debug("未配置默认项目，跳过项目设备缓存同步");
            return;
        }

        try {
            JsonNode convertersNode = openApiClient.listStreamConverters(null);
            log.debug("OpenAPI码流转发器列表原始数据: {}", convertersNode);
            if (convertersNode == null || !convertersNode.has("data")) {
                log.debug("OpenAPI码流转发器列表为空");
                return;
            }

            Set<String> deviceSnSet = new HashSet<>();
            JsonNode dataNode = convertersNode.get("data");
            if (dataNode.has("list")) {
                for (JsonNode converter : dataNode.get("list")) {
                    String sn = converter.has("sn") ? converter.get("sn").asText() : "";
                    if (StrUtil.isNotBlank(sn)) {
                        deviceSnSet.add(sn);
                    }
                }
            }

            String cacheKey = "project_devices:" + defaultProjectUuid;
            stringRedisTemplate.delete(cacheKey);
            if (!deviceSnSet.isEmpty()) {
                stringRedisTemplate.opsForSet().add(cacheKey, deviceSnSet.toArray(new String[0]));
            }
            stringRedisTemplate.expire(cacheKey, 10, TimeUnit.MINUTES);

            log.info("项目设备缓存同步完成，项目={}，设备数={}", defaultProjectUuid, deviceSnSet.size());
        } catch (Exception e) {
            log.error("项目设备缓存同步失败", e);
        }
    }

    /**
     * 全量刷新设备：TRUNCATE device 表 + 从 OpenAPI /openapi/v2.0/project/device 重新拉取入库。
     * 供 admin「设备同步」按钮调用（POST /devices/sync-names）。
     * org/project 用配置 defaultOrganizationUuid/defaultProjectUuid（与 MQTT OSD 顶层 OrganizationUUID 一致）。
     */
    public synchronized void fullRefreshDevicesFromOpenApi() {
        String orgUuid = openApiProperties.getDefaultOrganizationUuid();
        String projectUuid = openApiProperties.getDefaultProjectUuid();
        if (StrUtil.isBlank(projectUuid)) {
            throw new RuntimeException("未配置 defaultProjectUuid，无法拉取项目设备");
        }
        log.info("[设备全量同步] 开始 org={} project={}", orgUuid, projectUuid);

        // 1. 先拉取（不先清空，避免 OpenAPI 失败导致 device 表空）
        JsonNode resp = openApiClient.listProjectDevices(projectUuid);
        if (resp == null) {
            throw new RuntimeException("OpenAPI 返回空");
        }
        if (!resp.has("code") || resp.get("code").asInt() != 0) {
            throw new RuntimeException("OpenAPI 返回错误: " + resp);
        }
        JsonNode listNode = (resp.has("data") && resp.get("data").has("list"))
                ? resp.get("data").get("list") : null;
        if (listNode == null || !listNode.isArray() || listNode.size() == 0) {
            throw new RuntimeException("OpenAPI 返回设备列表为空");
        }

        // 2. 构建待入库 Device 列表（gateway 机场 + drone 飞机）
        List<Device> devices = new ArrayList<>();
        int gatewayCount = 0, droneCount = 0;
        for (JsonNode item : listNode) {
            JsonNode gw = item.has("gateway") ? item.get("gateway") : null;
            JsonNode dr = item.has("drone") ? item.get("drone") : null;
            String gatewaySn = (gw != null && gw.has("sn")) ? gw.get("sn").asText() : null;
            if (gw != null && StrUtil.isNotBlank(gatewaySn)) {
                devices.add(buildDeviceFromNode(gw, DeviceTypeConstants.GATEWAY_DOCK, orgUuid, projectUuid, null));
                gatewayCount++;
            }
            String droneSn = (dr != null && dr.has("sn")) ? dr.get("sn").asText() : null;
            if (dr != null && StrUtil.isNotBlank(droneSn)) {
                devices.add(buildDeviceFromNode(dr, DeviceTypeConstants.AIRCRAFT, orgUuid, projectUuid, gatewaySn));
                droneCount++;
            }
        }
        log.info("[设备全量同步] 拉取成功 机场={}台 飞机={}台", gatewayCount, droneCount);

        // 3. 清空 device 表（TRUNCATE，重置自增 ID，不可恢复）
        deviceMapper.truncateTable();
        log.info("[设备全量同步] device 表已清空（TRUNCATE）");

        // 4. 重新入库（saveOrUpdateDevice 内部按 sn 查，TRUNCATE 后全走 insert，并清 Redis 缓存）
        for (Device d : devices) {
            deviceService.saveOrUpdateDevice(d);
        }

        // 5. 保存相机信息（飞机/机场的 camera_list，可能为 null）
        for (JsonNode item : listNode) {
            JsonNode gw = item.has("gateway") ? item.get("gateway") : null;
            JsonNode dr = item.has("drone") ? item.get("drone") : null;
            if (dr != null && dr.has("camera_list") && !dr.get("camera_list").isNull()) {
                deviceService.saveCameras(dr.get("sn").asText(), dr.get("camera_list"));
            }
            if (gw != null && gw.has("camera_list") && !gw.get("camera_list").isNull()) {
                deviceService.saveCameras(gw.get("sn").asText(), gw.get("camera_list"));
            }
        }

        log.info("[设备全量同步] 完成 共入库 {} 条设备", devices.size());
    }

    /** 从 OpenAPI 返回的 gateway/drone 节点构建 Device 实体 */
    private Device buildDeviceFromNode(JsonNode node, int deviceType, String orgUuid, String projectUuid, String gatewaySn) {
        Device d = new Device();
        d.setSn(node.has("sn") ? node.get("sn").asText() : null);
        d.setDeviceName(node.has("callsign") ? node.get("callsign").asText() : null);
        JsonNode model = node.has("device_model") ? node.get("device_model") : null;
        if (model != null) {
            d.setDeviceModel(model.has("name") ? model.get("name").asText() : null);
            d.setModelKey(model.has("key") ? model.get("key").asText() : null);
        }
        d.setDeviceType(deviceType);
        boolean online = node.has("device_online_status") && node.get("device_online_status").asBoolean();
        d.setOnlineStatus(online ? DeviceTypeConstants.ONLINE_ONLINE : 0);
        d.setPairedStatus(DeviceTypeConstants.PAIRED_YES);
        d.setOrgUuid(orgUuid);
        d.setProjectUuid(projectUuid);
        d.setGatewaySn(gatewaySn);
        if (online) {
            d.setLastOnlineTime(LocalDateTime.now());
        }
        return d;
    }
}
