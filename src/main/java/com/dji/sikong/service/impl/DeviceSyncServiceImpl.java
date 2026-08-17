package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dji.sikong.common.DeviceTypeConstants;
import com.dji.sikong.config.DjiOpenApiProperties;
import com.dji.sikong.entity.Device;
import com.dji.sikong.openapi.DjiOpenApiClient;
import com.dji.sikong.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.HashSet;
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
}
