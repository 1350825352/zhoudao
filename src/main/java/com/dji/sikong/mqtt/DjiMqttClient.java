package com.dji.sikong.mqtt;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.dji.sikong.common.DeviceTypeConstants;
import com.dji.sikong.common.RedisKeyConstants;
import com.dji.sikong.entity.AircraftCamera;
import com.dji.sikong.entity.Device;
import com.dji.sikong.mapper.AircraftCameraMapper;
import com.dji.sikong.service.DeviceRealtimeStatusService;
import com.dji.sikong.service.DeviceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DjiMqttClient {

    @Resource
    private com.dji.sikong.config.DjiMqttProperties djiMqttProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DeviceService deviceService;

    @Resource
    private com.dji.sikong.config.DjiOpenApiProperties openApiProperties;

    @Resource
    private DeviceRealtimeStatusService realtimeStatusService;

    @Resource
    private AircraftCameraMapper aircraftCameraMapper;

    @org.springframework.context.annotation.Lazy
    @Resource
    private com.dji.sikong.service.LiveStreamService liveStreamService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MqttClient mqttClient;

    @PostConstruct
    public void init() {
        try {
            connect();
            subscribeTopics();
            log.info("=== DJI 上云API MQTT客户端初始化成功 ===");
            log.info("Broker: {}, ClientId: {}", djiMqttProperties.getHost(), djiMqttProperties.getClientId());
        } catch (Exception e) {
            log.error("DJI MQTT客户端初始化失败, 原因: {}", e.getMessage(), e);
        }
    }

    private void connect() throws MqttException {
        mqttClient = new MqttClient(djiMqttProperties.getHost(), djiMqttProperties.getClientId(),
                new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(djiMqttProperties.getUsername());
        options.setPassword(djiMqttProperties.getPassword().toCharArray());
        options.setConnectionTimeout(djiMqttProperties.getConnectionTimeout());
        options.setKeepAliveInterval(djiMqttProperties.getKeepAlive());
        options.setCleanSession(djiMqttProperties.getCleanSession());
        options.setAutomaticReconnect(djiMqttProperties.getAutomaticReconnect());

        mqttClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("MQTT连接成功, reconnect={}, serverURI={}", reconnect, serverURI);
                if (reconnect) {
                    try {
                        subscribeTopics();
                    } catch (MqttException e) {
                        log.error("重连后重新订阅Topic失败", e);
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.error("MQTT连接丢失: {}", cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleMessage(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });

        mqttClient.connect(options);
    }

    private void subscribeTopics() throws MqttException {
        List<String> topics = djiMqttProperties.getSubscribeTopics();
        if (topics == null || topics.isEmpty()) return;

        int[] qos = new int[topics.size()];
        for (int i = 0; i < topics.size(); i++) qos[i] = 1;

        mqttClient.subscribe(topics.toArray(new String[0]), qos);
        log.info("MQTT订阅Topic成功: {}", topics);
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            if (StrUtil.isBlank(payload)) return;

            JsonNode root = OBJECT_MAPPER.readTree(payload);
            String gateway = root.has("gateway") ? root.get("gateway").asText() : null;
            String method = root.has("method") ? root.get("method").asText() : null;

            log.debug("收到MQTT消息 topic={}, gateway={}, method={}", topic, gateway, method);

            if (topic.startsWith("sys/product/") && topic.endsWith("/status")) {
                handleStatus(gateway, root);
                replyStatus(gateway, root);
            } else if (topic.startsWith("thing/product/") && topic.endsWith("/osd")) {
                String topicSn = extractDeviceSnFromTopic(topic);
                String sn = (root.has("sn") && StrUtil.isNotBlank(root.get("sn").asText()))
                        ? root.get("sn").asText() : topicSn;
                String gwSn = StrUtil.isNotBlank(gateway) ? gateway : topicSn;
                handleOsd(sn, gwSn, root);
            } else if (topic.startsWith("thing/product/") && topic.endsWith("/state")) {
                String topicSn = extractDeviceSnFromTopic(topic);
                String sn = (root.has("sn") && StrUtil.isNotBlank(root.get("sn").asText()))
                        ? root.get("sn").asText() : topicSn;
                String gwSn = StrUtil.isNotBlank(gateway) ? gateway : topicSn;
                handleOsd(sn, gwSn, root);
            } else if (topic.startsWith("thing/product/") && topic.endsWith("/events")) {
                handleEvents(gateway, root);
            } else if (topic.startsWith("thing/product/") && topic.endsWith("/requests")) {
                handleRequests(gateway, root);
            } else if (topic.startsWith("thing/product/") && topic.endsWith("/services_reply")) {
                handleServicesReply(gateway, root);
            } else if (topic.startsWith("thing/product/") && topic.endsWith("/property/post")) {
                String topicSn = extractDeviceSnFromTopic(topic);
                String sn = (root.has("sn") && StrUtil.isNotBlank(root.get("sn").asText()))
                        ? root.get("sn").asText() : topicSn;
                System.out.println("\n========== [MQTT-属性上报-调试] ==========");
                System.out.println("[property/post] topic     : " + topic);
                System.out.println("[property/post] deviceSn  : " + sn);
                System.out.println("[property/post] payload前500字: " + (payload.length()>500?payload.substring(0,500)+"...":payload));
                System.out.println("====================================");
                handlePropertyPost(sn, root);
            } else if (topic.startsWith("thing/product/") && topic.endsWith("/drc/up") || topic.startsWith("thing/product/") && topic.contains("/drc/")) {
                // ========== 调试: 所有drc类消息都打完整日志 ==========
                System.out.println("\n========== [DRC消息调试] ==========");
                System.out.println("[DRC] topic     : " + topic);
                System.out.println("[DRC] gateway   : " + gateway);
                System.out.println("[DRC] method    : " + method);
                System.out.println("[DRC] payload前500字: " + (payload.length()>500?payload.substring(0,500)+"...":payload));
                System.out.println("====================================");
                // ========== END ==========
                String topicSn = extractDeviceSnFromTopic(topic);
                String gwSn = StrUtil.isNotBlank(gateway) ? gateway : topicSn;
                handleDrcUp(gwSn, method, root, topic, payload);
            }

        } catch (Exception e) {
            log.error("处理MQTT消息异常, topic={}", topic, e);
        }
    }

    private String extractDeviceSnFromTopic(String topic) {
        // topic 格式: sys|thing/product/{device_sn}/...
        // split 后 parts[0]=thing, parts[1]=product, parts[2]=device_sn, parts[3]=osd|state|...
        String[] parts = topic.split("/");
        if (parts.length >= 3) return parts[2];
        return null;
    }

    // ========== 处理 sys/status (设备拓扑 + 上下线) ==========
    private void handleStatus(String gateway, JsonNode root) {
        try {
            if (!root.has("data")) return;
            JsonNode data = root.get("data");

            // 1. 处理网关上线
            saveOrUpdateGatewayDevice(gateway, data);

            // 2. 处理子设备列表
            JsonNode subDevices = data.get("sub_devices");
            if (subDevices != null && subDevices.isArray()) {
                for (JsonNode sub : subDevices) {
                    String sn = sub.has("sn") ? sub.get("sn").asText() : null;
                    Integer type = sub.has("type") ? sub.get("type").asInt() : null;
                    String index = sub.has("index") ? sub.get("index").asText() : null;
                    if (StrUtil.isBlank(sn)) continue;

                    saveOrUpdateSubDevice(sn, type, index, gateway);
                }
                updateSubDeviceOnlineStatus(gateway, subDevices.size());
            } else {
                updateSubDeviceOnlineStatus(gateway, 0);
            }

            // 3. Redis缓存在线状态
            cacheOnlineStatus(gateway, 1);
            log.info("处理拓扑更新完成 gateway={}, 子设备数={}",
                    gateway, (subDevices == null ? 0 : subDevices.size()));

        } catch (Exception e) {
            log.error("处理sys/status异常 gateway={}", gateway, e);
        }
    }

    private void saveOrUpdateGatewayDevice(String gateway, JsonNode data) {
        if (StrUtil.isBlank(gateway)) return;
        Device device = deviceService.getBySn(gateway);
        if (device == null) device = new Device();
        device.setSn(gateway);
        if (device.getDeviceType() == null) device.setDeviceType(DeviceTypeConstants.GATEWAY_DOCK);
        device.setOnlineStatus(DeviceTypeConstants.ONLINE_ONLINE);
        device.setLastOnlineTime(LocalDateTime.now());
        device.setProjectUuid(checkDeviceProjectBelong(gateway));
        deviceService.saveOrUpdateDevice(device);
    }

    private void saveOrUpdateSubDevice(String sn, Integer type, String index, String gatewaySn) {
        Device device = deviceService.getBySn(sn);
        if (device == null) device = new Device();
        device.setSn(sn);
        device.setGatewaySn(gatewaySn);
        if (type != null) device.setDeviceType(inferDeviceTypeByCloudType(type));
        device.setPairedStatus(DeviceTypeConstants.PAIRED_YES);
        device.setOnlineStatus(DeviceTypeConstants.ONLINE_ONLINE);
        device.setLastOnlineTime(LocalDateTime.now());
        device.setProjectUuid(checkDeviceProjectBelong(sn));
        deviceService.saveOrUpdateDevice(device);

        cacheOnlineStatus(sn, 1);
    }

    private Integer inferDeviceTypeByCloudType(int cloudType) {
        // 大疆上云API cloud type: 116=飞行器, 其他根据实际
        if (cloudType == 116 || cloudType == 119) return DeviceTypeConstants.AIRCRAFT;
        return DeviceTypeConstants.CAMERA;
    }

    private String checkDeviceProjectBelong(String sn) {
        String defaultProjectUuid = openApiProperties.getDefaultProjectUuid();
        if (StrUtil.isBlank(defaultProjectUuid)) {
            return null;
        }

        String cacheKey = "project_devices:" + defaultProjectUuid;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(cacheKey, sn);

        if (Boolean.TRUE.equals(isMember)) {
            return defaultProjectUuid;
        }

        return null;
    }

    private void updateSubDeviceOnlineStatus(String gatewaySn, int subCount) {
        // 如果sub_devices为空数组，表示所有子设备离线
        if (subCount == 0) {
            List<Device> subs = deviceService.lambdaQuery()
                    .eq(Device::getGatewaySn, gatewaySn).list();
            for (Device d : subs) {
                d.setOnlineStatus(DeviceTypeConstants.ONLINE_OFFLINE);
                d.setLastOfflineTime(LocalDateTime.now());
                deviceService.saveOrUpdateDevice(d);
                cacheOnlineStatus(d.getSn(), 0);
            }
        }
    }

    // ========== 回复 status_reply ==========
    private void replyStatus(String gateway, JsonNode root) {
        try {
            if (StrUtil.isBlank(gateway)) return;
            String replyTopic = "sys/product/" + gateway + "/status_reply";
            String tid = root.has("tid") ? root.get("tid").asText() : "";
            String bid = root.has("bid") ? root.get("bid").asText() : "";

            java.util.Map<String, Object> reply = new java.util.HashMap<>();
            reply.put("tid", tid);
            reply.put("bid", bid);
            reply.put("timestamp", System.currentTimeMillis());
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("result", 0);
            reply.put("data", data);

            publish(replyTopic, JSONUtil.toJsonStr(reply), 1);
            log.debug("回复 status_reply gateway={}", gateway);
        } catch (Exception e) {
            log.error("回复status_reply异常 gateway={}", gateway, e);
        }
    }

    // ========== 处理 thing/osd (实时状态) ==========
    private void handleOsd(String deviceSn, String gatewaySn, JsonNode root) {
        try {
            if (StrUtil.isBlank(deviceSn) || !root.has("data")) return;
            JsonNode data = root.get("data");

            // 0. 根据OSD字段推断设备类型，并确保device表中存在对应记录
            ensureDeviceExists(deviceSn, gatewaySn, data);

            // 1. 入库+缓存实时状态
            realtimeStatusService.saveOrUpdateFromOsd(deviceSn, gatewaySn, data);

            // 2. 同时补充设备信息（如果osd带了子设备信息）
            JsonNode subDevice = data.get("sub_device");
            if (subDevice != null && subDevice.isObject()) {
                String subSn = subDevice.has("device_sn") ? subDevice.get("device_sn").asText() : null;
                String modelKey = subDevice.has("device_model_key") ? subDevice.get("device_model_key").asText() : null;
                Integer onlineStatus = subDevice.has("device_online_status") ? subDevice.get("device_online_status").asInt() : null;
                if (StrUtil.isNotBlank(subSn)) {
                    Device d = deviceService.getBySn(subSn);
                    if (d == null) d = new Device();
                    d.setSn(subSn);
                    d.setGatewaySn(deviceSn);
                    if (d.getDeviceType() == null) d.setDeviceType(DeviceTypeConstants.AIRCRAFT);
                    if (StrUtil.isNotBlank(modelKey)) d.setModelKey(modelKey);
                    if (onlineStatus != null) d.setOnlineStatus(onlineStatus);
                    d.setOnlineStatus(DeviceTypeConstants.ONLINE_ONLINE);
                    d.setLastOnlineTime(java.time.LocalDateTime.now());
                    deviceService.saveOrUpdateDevice(d);
                    cacheOnlineStatus(subSn, 1);
                }
            }

            // 3. 解析相机信息（osd中cameras或camera_list）
            JsonNode host = data.has("host") ? data.get("host") : data;
            JsonNode cameraList = host.has("cameras") ? host.get("cameras") : data.get("camera_list");
            if (cameraList != null && cameraList.isArray()) {
                deviceService.saveCameras(deviceSn, cameraList);
            }

        } catch (Exception e) {
            log.error("处理osd异常 deviceSn={}", deviceSn, e);
        }
    }

    /** 根据OSD特征字段判断设备类型，并确保device表中有记录 */
    private void ensureDeviceExists(String deviceSn, String gatewaySn, JsonNode data) {
        if (StrUtil.isBlank(deviceSn)) return;
        JsonNode host = data.has("host") ? data.get("host") : data;
        Device d = deviceService.getBySn(deviceSn);
        if (d == null) d = new Device();
        d.setSn(deviceSn);

        // 通过best_link_gateway关联飞行器到机场（OSD中host.best_link_gateway即为机场SN）
        if (host.has("best_link_gateway")) {
            String blg = host.get("best_link_gateway").asText();
            if (StrUtil.isNotBlank(blg) && !blg.equals(deviceSn)) {
                d.setGatewaySn(blg);
            }
        }
        if (StrUtil.isBlank(d.getGatewaySn()) && StrUtil.isNotBlank(gatewaySn) && !deviceSn.equals(gatewaySn)) {
            d.setGatewaySn(gatewaySn);
        }

        if (d.getDeviceType() == null) {
            boolean hasFlightData =
                    host.has("height") || host.has("latitude") || host.has("longitude")
                            || host.has("position_state") || host.has("attitude_head")
                            || host.has("horizontal_speed") || host.has("total_flight_sorties")
                            || host.has("cameras") || host.has("best_link_gateway")
                            || host.has("home_distance");
            boolean hasDockData =
                    host.has("drone_in_dock") || host.has("cover_state")
                            || host.has("drone_battery_maintenance_info")
                            || host.has("acdc_power_input") || host.has("working_current")
                            || host.has("electric_supply_voltage") || host.has("job_number")
                            || host.has("backup_battery") || host.has("deployment_mode");
            if (hasDockData) {
                d.setDeviceType(DeviceTypeConstants.GATEWAY_DOCK);
            } else if (hasFlightData) {
                d.setDeviceType(DeviceTypeConstants.AIRCRAFT);
                if (StrUtil.isBlank(d.getGatewaySn())) {
                    d.setGatewaySn(deviceSn);
                }
            } else {
                d.setDeviceType(DeviceTypeConstants.GATEWAY_DOCK);
            }
        }
        // 每次都更新gatewaySn（如果best_link_gateway提供了有效的机场关联）
        if (host.has("best_link_gateway")) {
            String blg = host.get("best_link_gateway").asText();
            if (StrUtil.isNotBlank(blg) && !blg.equals(deviceSn)) {
                d.setGatewaySn(blg);
            }
        }
        d.setOnlineStatus(DeviceTypeConstants.ONLINE_ONLINE);
        d.setLastOnlineTime(java.time.LocalDateTime.now());
        deviceService.saveOrUpdateDevice(d);
        cacheOnlineStatus(deviceSn, 1);
    }

    // ========== 处理 property/post (属性上报，包含航线任务进度) ==========
    private void handlePropertyPost(String deviceSn, JsonNode root) {
        try {
            if (StrUtil.isBlank(deviceSn) || !root.has("data")) return;
            JsonNode data = root.get("data");
            
            String waylineId = null;
            if (data.has("wayline_id")) {
                waylineId = data.get("wayline_id").asText(null);
            }
            
            if (StrUtil.isNotBlank(waylineId)) {
                log.info("[MQTT-属性上报] deviceSn={} 收到航线任务进度 wayline_id={}", deviceSn, waylineId);
                
                com.dji.sikong.entity.DeviceRealtimeStatus status = realtimeStatusService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dji.sikong.entity.DeviceRealtimeStatus>()
                                .eq(com.dji.sikong.entity.DeviceRealtimeStatus::getDeviceSn, deviceSn));
                
                if (status != null) {
                    status.setWaylineUuid(waylineId);
                    realtimeStatusService.updateById(status);
                    log.info("[MQTT-属性上报] 更新device_realtime_status wayline_uuid={} for deviceSn={}", waylineId, deviceSn);
                }
                
                com.dji.sikong.entity.Device device = deviceService.getBySn(deviceSn);
                if (device != null && device.getDeviceType() != null && device.getDeviceType() == DeviceTypeConstants.GATEWAY_DOCK) {
                    List<com.dji.sikong.entity.Device> aircrafts = deviceService.lambdaQuery()
                            .eq(com.dji.sikong.entity.Device::getGatewaySn, deviceSn)
                            .eq(com.dji.sikong.entity.Device::getDeviceType, DeviceTypeConstants.AIRCRAFT)
                            .list();
                    
                    for (com.dji.sikong.entity.Device aircraft : aircrafts) {
                        com.dji.sikong.entity.DeviceRealtimeStatus aircraftStatus = realtimeStatusService.getOne(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dji.sikong.entity.DeviceRealtimeStatus>()
                                        .eq(com.dji.sikong.entity.DeviceRealtimeStatus::getDeviceSn, aircraft.getSn()));
                        
                        if (aircraftStatus != null) {
                            aircraftStatus.setWaylineUuid(waylineId);
                            realtimeStatusService.updateById(aircraftStatus);
                            log.info("[MQTT-属性上报] 同步机场航线UUID到飞机: airportSn={}, aircraftSn={}, waylineUuid={}", 
                                    deviceSn, aircraft.getSn(), waylineId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[MQTT-属性上报] 处理异常 deviceSn={}", deviceSn, e);
        }
    }

    // ========== 处理 events ==========
    private void handleEvents(String gateway, JsonNode root) {
        try {
            if (!root.has("need_reply") || root.get("need_reply").asInt() != 1) return;

            String tid = root.has("tid") ? root.get("tid").asText() : "";
            String bid = root.has("bid") ? root.get("bid").asText() : "";
            String method = root.has("method") ? root.get("method").asText() : "";
            String replyTopic = "thing/product/" + gateway + "/events_reply";

            java.util.Map<String, Object> reply = new java.util.HashMap<>();
            reply.put("tid", tid);
            reply.put("bid", bid);
            reply.put("timestamp", System.currentTimeMillis());
            reply.put("method", method);
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("result", 0);
            reply.put("data", data);

            publish(replyTopic, JSONUtil.toJsonStr(reply), 1);
            log.debug("回复 events_reply gateway={}, method={}", gateway, method);
        } catch (Exception e) {
            log.error("处理events异常 gateway={}", gateway, e);
        }
    }

    // ========== 处理 requests (机场上云必备：config/STS/绑定码等) ==========
    private void handleRequests(String gateway, JsonNode root) {
        try {
            if (!root.has("method")) return;
            String method = root.get("method").asText();
            String tid = root.has("tid") ? root.get("tid").asText() : "";
            String bid = root.has("bid") ? root.get("bid").asText() : "";
            String replyTopic = "thing/product/" + gateway + "/requests_reply";

            java.util.Map<String, Object> reply = new java.util.HashMap<>();
            reply.put("tid", tid);
            reply.put("bid", bid);
            reply.put("timestamp", System.currentTimeMillis());
            reply.put("method", method);
            java.util.Map<String, Object> output = new java.util.HashMap<>();
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("result", 0);
            data.put("output", output);
            reply.put("data", data);

            switch (method) {
                case "config":
                    // 实际项目中应从数据库配置中读取License参数
                    output.put("license", "your-license-here");
                    output.put("appId", "your-appId-here");
                    output.put("appKey", "your-appKey-here");
                    output.put("appLicense", "your-appLicense-here");
                    output.put("developerAccount", "your-developer-account");
                    output.put("sdkVersion", "0.3.2");
                    output.put("tenantId", "your-tenantId");
                    break;

                case "airport_organization_get":
                    output.put("bind_code", "example-bind-code");
                    output.put("organization_name", "示例组织");
                    output.put("organization_id", "example-org-id");
                    break;

                case "airport_organization_bind":
                    // 实际项目中处理绑定码绑定逻辑
                    output.put("organization_id", "example-org-id");
                    output.put("organization_name", "示例组织");
                    break;

                case "airport_bind_status":
                    output.put("bind_status", 1);
                    output.put("organization_id", "example-org-id");
                    output.put("organization_name", "示例组织");
                    break;

                case "storage_config_get":
                    // 实际项目中返回对象存储STS临时凭证
                    output.put("provider", "MINIO");
                    output.put("endpoint", "http://your-minio:9000");
                    output.put("bucket", "sikong-media");
                    output.put("access_key", "minioadmin");
                    output.put("secret_key", "minioadmin");
                    output.put("token", "");
                    output.put("region", "us-east-1");
                    java.util.Map<String, Object> dir = new java.util.HashMap<>();
                    dir.put("upload_dir", "upload-dir");
                    dir.put("host", "http://your-minio:9000");
                    output.put("dir", dir);
                    break;

                default:
                    break;
            }

            publish(replyTopic, JSONUtil.toJsonStr(reply), 1);
            log.info("回复 requests_reply gateway={}, method={}", gateway, method);

        } catch (Exception e) {
            log.error("处理requests异常 gateway={}", gateway, e);
        }
    }

    // ========== 处理 services_reply ==========
    private void handleServicesReply(String gateway, JsonNode root) {
        try {
            String tid = root.has("tid") ? root.get("tid").asText() : "";
            String method = root.has("method") ? root.get("method").asText() : "";
            JsonNode data = root.get("data");
            Integer result = (data != null && data.has("result")) ? data.get("result").asInt() : null;

            // 通过tid关联到之前下发的任务/直播
            String cacheKey = RedisKeyConstants.TASK_TID_BID_KEY + tid;
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                Long taskId = Long.parseLong(cached);
                com.dji.sikong.entity.FlightTask task = new com.dji.sikong.entity.FlightTask();
                task.setId(taskId);
                task.setResultCode(result);
                task.setResponseJson(OBJECT_MAPPER.writeValueAsString(root));
                if (result != null && result == 0) {
                    task.setTaskStatus(1);
                    task.setStatusMessage("已下发至设备，等待执行");
                    task.setActualStartTime(LocalDateTime.now());
                } else {
                    task.setTaskStatus(6);
                    task.setStatusMessage("下发失败: result=" + result);
                    task.setActualEndTime(LocalDateTime.now());
                }
                com.dji.sikong.mapper.FlightTaskMapper flightTaskMapper =
                        (com.dji.sikong.mapper.FlightTaskMapper)
                                org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext()
                                        .getBean("flightTaskMapper");
                flightTaskMapper.updateById(task);
                stringRedisTemplate.delete(cacheKey);
                log.info("services_reply关联任务成功 taskId={}, method={}, result={}", taskId, method, result);
            }

            // 直播同理
            cacheKey = RedisKeyConstants.LIVE_TID_BID_KEY + tid;
            cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(cached)) {
                Long liveId = Long.parseLong(cached);
                com.dji.sikong.entity.LiveStream ls = new com.dji.sikong.entity.LiveStream();
                ls.setId(liveId);
                ls.setResponseJson(OBJECT_MAPPER.writeValueAsString(root));
                if (result != null && result == 0) {
                    ls.setStatus(1);
                    ls.setStartTime(LocalDateTime.now());
                } else {
                    ls.setStatus(3);
                }
                com.dji.sikong.mapper.LiveStreamMapper liveMapper =
                        (com.dji.sikong.mapper.LiveStreamMapper)
                                org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext()
                                        .getBean("liveStreamMapper");
                liveMapper.updateById(ls);
                stringRedisTemplate.delete(cacheKey);
                log.info("services_reply关联直播成功 liveId={}, method={}, result={}", liveId, method, result);
            }

        } catch (Exception e) {
            log.error("处理services_reply异常 gateway={}", gateway, e);
        }
    }

    // ========== 处理 thing/product/{gatewaySn}/drc/up 远程控制状态上发 ==========

    /**
     * 飞行器状态信息上报(method=drc_drone_state_push) → 仅依据mode_code判断开/关码流
     *   飞行集合(开码流): 3手动飞行 / 4自动起飞 / 5航线飞行 / 6全景拍照 / 7智能跟随 / 8ADS-B躲避
     *                   / 9自动返航 / 10自动降落 / 11强制降落 / 12三桨叶降落
     *                   / 15APAS / 16虚拟摇杆 / 17指令飞行 / 19 / 20 / 21
     *   其他所有mode_code(包括地面集合中的0/1/2以及任何未定义mode) → 关闭码流转发器
     */
    private static final java.util.Set<Integer> FLYING_MODES = new java.util.HashSet<>(
            java.util.Arrays.asList(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 16, 17, 19, 20, 21));
    private static final java.util.Set<Integer> GROUND_LANDING_MODES = new java.util.HashSet<>(
            java.util.Arrays.asList(0, 1, 2));

    private void handleDrcUp(String gatewaySn, String method, JsonNode root, String topic, String fullPayload) {
        try {
            System.out.println("[drc-handle-1] 进入handleDrcUp, gatewaySn=" + gatewaySn + ", method=" + method + ", topic=" + topic);
            if (!"drc_drone_state_push".equals(method)) {
                System.out.println("[drc-handle-2-SKIP] method不是drc_drone_state_push,直接跳过");
                return;
            }
            if (StrUtil.isBlank(gatewaySn)) {
                System.out.println("[drc-handle-3-SKIP] gatewaySn为空,无法找到飞机");
                return;
            }
            if (!root.has("data")) {
                System.out.println("[drc-handle-4-SKIP] 消息里没有data节点");
                return;
            }
            JsonNode data = root.get("data");
            System.out.println("[drc-handle-5] data节点完整内容: " + (data == null ? "null" : data.toString()));
            if (!data.has("mode_code")) {
                System.out.println("[drc-handle-6-SKIP] data里没有mode_code字段");
                return;
            }
            int modeCode = data.get("mode_code").asInt();
            System.out.println("[drc-handle-7] 解析到mode_code=" + modeCode);

            // 通过gatewaySn找到配对的飞机SN
            String aircraftSn = deviceService.getAircraftSnByGatewaySn(gatewaySn);
            System.out.println("[drc-handle-8] 通过gatewaySn=" + gatewaySn + " → 查询到飞机SN=" + aircraftSn);
            if (StrUtil.isBlank(aircraftSn)) {
                System.out.println("[drc-handle-9-SKIP] ❌ 未找到配对飞机SN, method=" + method + " mode=" + modeCode);
                return;
            }
            // 起飞前先清掉OSD旧防重锁(防止被其他逻辑挡住)
            if (FLYING_MODES.contains(modeCode)) {
                try {
                    String flagKey = "live:auto_start:" + aircraftSn;
                    stringRedisTemplate.delete(flagKey);
                } catch (Exception ignore) {}
            }
            // ========== 统一调用DeviceRealtimeStatusService的mode_code处理入口
            //  OSD通道与DRC通道共用同一套逻辑:飞行立即开;非飞行连续5秒才关(防抖) ==========
            System.out.println("[drc-handle-统一入口] 调用realtimeStatusService.handleModeCode("
                    + aircraftSn + ", " + gatewaySn + ", " + modeCode + ")");
            realtimeStatusService.handleModeCode(aircraftSn, gatewaySn, modeCode);
        } catch (Exception e) {
            log.error("处理drc_drone_state_push异常 gateway={}", gatewaySn, e);
            e.printStackTrace();
        }
    }

    private String getDefaultCameraIndex(String aircraftSn) {
        try {
            if (StrUtil.isBlank(aircraftSn)) return "0";
            AircraftCamera cam = aircraftCameraMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AircraftCamera>()
                            .eq(AircraftCamera::getAircraftSn, aircraftSn)
                            .eq(AircraftCamera::getDeleted, 0)
                            .orderByAsc(AircraftCamera::getId)
                            .last(" LIMIT 1"));
            if (cam != null && StrUtil.isNotBlank(cam.getCameraIndex())) return cam.getCameraIndex();
        } catch (Exception ignore) {}
        return "99-0-0";
    }

    private void cacheOnlineStatus(String sn, int status) {
        try {
            String key = RedisKeyConstants.DEVICE_ONLINE_KEY + sn;
            stringRedisTemplate.opsForValue().set(key, String.valueOf(status),
                    RedisKeyConstants.REALTIME_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("缓存在线状态失败 sn={}", sn);
        }
    }

    // ========== 发布消息工具方法 ==========
    public boolean publish(String topic, String payload, int qos) {
        try {
            if (mqttClient == null || !mqttClient.isConnected()) {
                log.warn("MQTT客户端未连接，无法发布 topic={}", topic);
                return false;
            }
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            mqttClient.publish(topic, msg);
            return true;
        } catch (Exception e) {
            log.error("MQTT发布失败 topic={}", topic, e);
            return false;
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null) mqttClient.disconnect();
        } catch (Exception e) {
            log.warn("关闭MQTT客户端异常: {}", e.getMessage());
        }
    }
}
