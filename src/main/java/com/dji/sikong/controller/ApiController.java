package com.dji.sikong.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sikong.common.DeviceTypeConstants;
import com.dji.sikong.common.R;
import com.dji.sikong.config.DjiConfig;
import com.dji.sikong.config.DjiMediaProperties;
import com.dji.sikong.dto.DeviceRealtimeVO;
import com.dji.sikong.entity.Device;
import com.dji.sikong.entity.DeviceRealtimeStatus;
import com.dji.sikong.entity.FlightTask;
import com.dji.sikong.entity.LiveStream;
import com.dji.sikong.entity.Wayline;
import com.dji.sikong.service.DeviceRealtimeStatusService;
import com.dji.sikong.service.DeviceService;
import com.dji.sikong.service.FlightTaskService;
import com.dji.sikong.service.LiveStreamService;
import com.dji.sikong.service.WaylineService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import com.dji.sikong.entity.ZhimouDetectionResult;
import com.dji.sikong.mapper.ZhimouDetectionResultMapper;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping(value = "", produces = "application/json;charset=UTF-8")
@CrossOrigin(origins = "*")
public class ApiController {

    private static final int STABLE_PROBE_THRESHOLD = 3;
    private static final ConcurrentHashMap<String, Integer> STREAM_STABLE_COUNT = new ConcurrentHashMap<>();

    @Resource
    private DeviceService deviceService;

    @Resource
    private DeviceRealtimeStatusService realtimeStatusService;

    @Resource
    private WaylineService waylineService;

    @Resource
    private FlightTaskService flightTaskService;

    @Resource
    private DjiConfig djiConfig;

    @Resource
    private DjiMediaProperties mediaProperties;

    @Resource
    private LiveStreamService liveStreamService;

    @Resource
    private com.dji.sikong.service.impl.DeviceSyncServiceImpl deviceSyncService;

    @Resource
    private com.dji.sikong.openapi.DjiOpenApiClient openApiClient;

    @Resource
    private ZhimouDetectionResultMapper detectionResultMapper;

    @Resource
    private com.dji.sikong.mapper.ZhimouImageDetectionMapper zhimouImageDetectionMapper;

    @Resource
    private com.dji.sikong.service.TaskSwitchService taskSwitchService;

    @Resource
    private com.dji.sikong.mapper.FlightTaskMapper flightTaskMapper;

    @Resource
    private com.dji.sikong.service.MediaServerStreamProbeService streamProbeService;

    @Data
    public static class DeviceDTO {
        private String sn;
        private String name;
        private String kind;
        private String status;
        private Double lng;
        private Double lat;
        private Integer flighttaskStepCode;
    }

    @Data
    public static class DeviceStatsDTO {
        private Integer uavCount;
        private Integer airportCount;
        private Integer onlineCount;
        private Integer flyingCount;
    }

    @Data
    public static class UavStatusDTO {
        private String sn;
        private String name;
        private String status;
        private Integer mode;
        private String modeText;
        private Double height;
        private Double altitude;
        private Double elevation;
        private Double speed;
        private Integer battery;
        private Integer satelliteCount;
        private Double lng;
        private Double lat;
        private Double distanceToHome;
    }

    @Data
    public static class LiveVideoUrlDTO {
        private String sn;
        private String url;
        private String whepUrl;
        private Boolean available;
    }

    @Data
    public static class LineDTO {
        private String id;
        private String fileName;
        private String actionTypeFmt;
        private String modiTime;
        private String waylineUuid;
        private String downloadUrl;
    }

    @Data
    public static class AlertDTO {
        private String id;
        private String appName;
        private String ruleName;
        private String alarmTime;
        private String level;
        private String imageUrl;
        private Double lng;
        private Double lat;
        private String waylineUuid;
    }

    @GetMapping("/devices/list")
    public R<List<DeviceDTO>> getDeviceList() {
        String orgUuid = djiConfig.getDefaultOrganizationUuid();
        String projectUuid = djiConfig.getDefaultProjectUuid();
        
        log.info("[设备列表] orgUuid={}, projectUuid={}", orgUuid, projectUuid);

        List<Device> devices = deviceService.list(new LambdaQueryWrapper<Device>()
                .in(Device::getDeviceType, DeviceTypeConstants.AIRCRAFT, DeviceTypeConstants.GATEWAY_DOCK)
                .eq(Device::getDeleted, 0)
                .eq(StrUtil.isNotBlank(orgUuid), Device::getOrgUuid, orgUuid)
                .eq(StrUtil.isNotBlank(projectUuid), Device::getProjectUuid, projectUuid));

        Map<String, Integer> flightTaskStepMap = flightTaskService.list(new LambdaQueryWrapper<FlightTask>()
                        .eq(FlightTask::getDeleted, 0)
                        .eq(StrUtil.isNotBlank(projectUuid), FlightTask::getProjectUuid, projectUuid))
                .stream()
                .filter(t -> t.getGatewaySn() != null && t.getStepCode() != null)
                .collect(Collectors.toMap(FlightTask::getGatewaySn, FlightTask::getStepCode));

        Map<String, DeviceRealtimeStatus> rsMap = realtimeStatusService.list(new LambdaQueryWrapper<>())
                .stream()
                .collect(Collectors.toMap(DeviceRealtimeStatus::getDeviceSn, r -> r));

        List<DeviceDTO> result = devices.stream().map(d -> {
            DeviceDTO dto = new DeviceDTO();
            dto.setSn(d.getSn());
            String name = d.getDeviceName();
            if (StrUtil.isBlank(name)) {
                if (Objects.equals(d.getDeviceType(), DeviceTypeConstants.AIRCRAFT)) {
                    name = "无人机-" + d.getSn().substring(d.getSn().length() - 6);
                } else {
                    name = "机场-" + d.getSn().substring(d.getSn().length() - 6);
                }
            }
            dto.setName(name);
            dto.setKind(Objects.equals(d.getDeviceType(), DeviceTypeConstants.AIRCRAFT) ? "uav" : "airport");
            
            String statusStr;
            Integer online = d.getOnlineStatus();
            if (online != null && online == DeviceTypeConstants.ONLINE_ONLINE) {
                DeviceRealtimeStatus rs = rsMap.get(d.getSn());
                if (Objects.equals(d.getDeviceType(), DeviceTypeConstants.AIRCRAFT) && rs != null && rs.getDroneInDock() != null && rs.getDroneInDock() == 0) {
                    statusStr = "flying";
                } else {
                    statusStr = "online";
                }
            } else {
                statusStr = "offline";
            }
            dto.setStatus(statusStr);

            DeviceRealtimeStatus rs = rsMap.get(d.getSn());
            if (rs != null && rs.getLongitude() != null && rs.getLatitude() != null) {
                dto.setLng(rs.getLongitude().doubleValue());
                dto.setLat(rs.getLatitude().doubleValue());
            }

            if ("airport".equals(dto.getKind())) {
                if (rs != null && rs.getFlightStepCode() != null) {
                    dto.setFlighttaskStepCode(rs.getFlightStepCode());
                } else {
                    dto.setFlighttaskStepCode(flightTaskStepMap.get(d.getSn()));
                }
            }

            return dto;
        }).collect(Collectors.toList());

        return R.ok(result);
    }

    @GetMapping("/devices/stats")
    public R<DeviceStatsDTO> getDeviceStats() {
        String orgUuid = djiConfig.getDefaultOrganizationUuid();
        String projectUuid = djiConfig.getDefaultProjectUuid();

        List<Device> devices = deviceService.list(new LambdaQueryWrapper<Device>()
                .in(Device::getDeviceType, DeviceTypeConstants.AIRCRAFT, DeviceTypeConstants.GATEWAY_DOCK)
                .eq(Device::getDeleted, 0)
                .eq(StrUtil.isNotBlank(orgUuid), Device::getOrgUuid, orgUuid)
                .eq(StrUtil.isNotBlank(projectUuid), Device::getProjectUuid, projectUuid));

        long uavCount = devices.stream()
                .filter(d -> Objects.equals(d.getDeviceType(), DeviceTypeConstants.AIRCRAFT))
                .count();
        long airportCount = devices.stream()
                .filter(d -> Objects.equals(d.getDeviceType(), DeviceTypeConstants.GATEWAY_DOCK))
                .count();
        long onlineCount = devices.stream()
                .filter(d -> d.getOnlineStatus() != null && d.getOnlineStatus() == DeviceTypeConstants.ONLINE_ONLINE)
                .count();

        Map<String, DeviceRealtimeStatus> rsMap = realtimeStatusService.list(new LambdaQueryWrapper<>())
                .stream()
                .collect(Collectors.toMap(DeviceRealtimeStatus::getDeviceSn, r -> r));

        long flyingCount = devices.stream()
                .filter(d -> Objects.equals(d.getDeviceType(), DeviceTypeConstants.AIRCRAFT))
                .filter(d -> {
                    DeviceRealtimeStatus rs = rsMap.get(d.getSn());
                    return rs != null && rs.getDroneInDock() != null && rs.getDroneInDock() == 0;
                })
                .count();

        DeviceStatsDTO stats = new DeviceStatsDTO();
        stats.setUavCount((int) uavCount);
        stats.setAirportCount((int) airportCount);
        stats.setOnlineCount((int) onlineCount);
        stats.setFlyingCount((int) flyingCount);

        return R.ok(stats);
    }

    @GetMapping("/uavs/{sn}/status")
    public R<UavStatusDTO> getUavStatus(@PathVariable String sn) {
        DeviceRealtimeVO vo = deviceService.getRealtimeInfo(sn);
        if (vo == null) {
            return R.fail("设备不存在");
        }

        UavStatusDTO dto = new UavStatusDTO();
        dto.setSn(vo.getDeviceSn());
        dto.setName(vo.getDeviceName());
        
        String statusStr;
        Integer online = vo.getOnlineStatus();
        if (online != null && online == DeviceTypeConstants.ONLINE_ONLINE) {
            if (vo.getDroneInDock() != null && vo.getDroneInDock() == 0) {
                statusStr = "flying";
            } else {
                statusStr = "online";
            }
        } else {
            statusStr = "offline";
        }
        dto.setStatus(statusStr);

        dto.setMode(vo.getDeviceMode());
        dto.setModeText(getModeText(vo.getDeviceMode()));
        
        if (vo.getHeight() != null) dto.setHeight(vo.getHeight().doubleValue());
        
        double speed = 0.0;
        if (vo.getVerticalSpeed() != null && vo.getHorizontalSpeed() != null) {
            double vv = vo.getVerticalSpeed().doubleValue();
            double hv = vo.getHorizontalSpeed().doubleValue();
            speed = Math.sqrt(vv * vv + hv * hv);
        }
        dto.setSpeed(speed);
        
        if (vo.getBatteryPercent() != null) dto.setBattery(vo.getBatteryPercent());
        if (vo.getGpsSatelliteCount() != null) dto.setSatelliteCount(vo.getGpsSatelliteCount());
        
        if (vo.getLongitude() != null) dto.setLng(vo.getLongitude().doubleValue());
        if (vo.getLatitude() != null) dto.setLat(vo.getLatitude().doubleValue());
        if (vo.getAltitude() != null) dto.setAltitude(vo.getAltitude().doubleValue());
        if (vo.getElevation() != null) dto.setElevation(vo.getElevation().doubleValue());
        if (vo.getDistanceToHome() != null) dto.setDistanceToHome(vo.getDistanceToHome().doubleValue());

        return R.ok(dto);
    }

    @GetMapping("/uavs/{sn}/video/live")
    public R<LiveVideoUrlDTO> getUavLiveVideoUrl(@PathVariable String sn, HttpServletRequest request) {
        LiveVideoUrlDTO dto = new LiveVideoUrlDTO();
        dto.setSn(sn);

        boolean internal = isInternalRequest(request);
        log.info("[视频流] 实时直播请求, sn={}, 内网={}", sn, internal);

        LiveStream live = getActiveLiveStream(sn);
        if (live != null) {
            String playableUrl = streamProbeService.probePlayableHttpFlvUrl(live, internal);
            if (StrUtil.isNotBlank(playableUrl)) {
                int count = STREAM_STABLE_COUNT.getOrDefault(sn, 0) + 1;
                STREAM_STABLE_COUNT.put(sn, count);
                if (count >= STABLE_PROBE_THRESHOLD) {
                    dto.setUrl(playableUrl);
                    dto.setWhepUrl(flvUrlToWhepUrl(playableUrl));
                    dto.setAvailable(true);
                    return R.ok(dto);
                } else {
                    dto.setUrl(playableUrl);
                    dto.setWhepUrl(flvUrlToWhepUrl(playableUrl));
                    dto.setAvailable(false);
                    log.info("[视频流] 流可播放但未稳定, sn={}, 连续成功={}/{}", sn, count, STABLE_PROBE_THRESHOLD);
                    return R.ok(dto);
                }
            } else {
                STREAM_STABLE_COUNT.remove(sn);
            }
        } else {
            STREAM_STABLE_COUNT.remove(sn);
        }

        dto.setAvailable(false);
        dto.setUrl("");
        return R.ok(dto);
    }

    @GetMapping("/uavs/{sn}/video/recognition")
    public R<LiveVideoUrlDTO> getUavRecognitionVideoUrl(@PathVariable String sn, HttpServletRequest request) {
        LiveVideoUrlDTO dto = new LiveVideoUrlDTO();
        dto.setSn(sn);

        boolean internal = isInternalRequest(request);
        log.info("[视频流] 实时识别请求, sn={}, 内网={}", sn, internal);

        LiveStream live = getActiveLiveStream(sn);
        if (live != null && live.getZhimouStatus() != null && live.getZhimouStatus() == 1) {
            String url = streamProbeService.buildAiRecognitionPullUrl(live, internal);
            if (StrUtil.isNotBlank(url) && streamProbeService.isStreamPlayable(url)) {
                int count = STREAM_STABLE_COUNT.getOrDefault(sn + ":rec", 0) + 1;
                STREAM_STABLE_COUNT.put(sn + ":rec", count);
                if (count >= STABLE_PROBE_THRESHOLD) {
                    dto.setUrl(url);
                    dto.setWhepUrl(flvUrlToWhepUrl(url));
                    dto.setAvailable(true);
                    return R.ok(dto);
                } else {
                    dto.setUrl(url);
                    dto.setWhepUrl(flvUrlToWhepUrl(url));
                    dto.setAvailable(false);
                    log.info("[视频流] 识别流可播放但未稳定, sn={}, 连续成功={}/{}", sn, count, STABLE_PROBE_THRESHOLD);
                    return R.ok(dto);
                }
            } else {
                STREAM_STABLE_COUNT.remove(sn + ":rec");
            }
        } else {
            STREAM_STABLE_COUNT.remove(sn + ":rec");
        }

        dto.setAvailable(false);
        dto.setUrl("");
        return R.ok(dto);
    }

    private LiveStream getActiveLiveStream(String sn) {
        if (StrUtil.isBlank(sn)) return null;
        LambdaQueryWrapper<LiveStream> qw = new LambdaQueryWrapper<>();
        qw.eq(LiveStream::getAircraftSn, sn)
                .eq(LiveStream::getDeleted, 0)
                .in(LiveStream::getStatus, 0, 1)
                .orderByDesc(LiveStream::getId)
                .last("LIMIT 1");
        LiveStream live = liveStreamService.getOne(qw);
        if (live == null) {
            qw = new LambdaQueryWrapper<>();
            qw.eq(LiveStream::getGatewaySn, sn)
                    .eq(LiveStream::getDeleted, 0)
                    .in(LiveStream::getStatus, 0, 1)
                    .orderByDesc(LiveStream::getId)
                    .last("LIMIT 1");
            live = liveStreamService.getOne(qw);
        }
        return live;
    }

    private String buildStreamKey(LiveStream live) {
        StringBuilder sb = new StringBuilder();
        String sn = StrUtil.isNotBlank(live.getAircraftSn()) ? live.getAircraftSn() : live.getGatewaySn();
        if (StrUtil.isNotBlank(sn)) sb.append(sn);
        else sb.append("live-").append(live.getId());
        if (StrUtil.isNotBlank(live.getCameraIndex())) sb.append('/').append(live.getCameraIndex());
        return sb.toString();
    }

    private String extractSecret(String pushUrl) {
        if (StrUtil.isBlank(pushUrl)) return "";
        int q = pushUrl.indexOf('?');
        if (q < 0) return "";
        String qs = pushUrl.substring(q + 1);
        for (String pair : qs.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            if ("secret".equalsIgnoreCase(k)) {
                return eq >= 0 ? pair.substring(eq + 1) : "";
            }
        }
        return "";
    }

    private String buildLivePullUrl(LiveStream live) {
        if (live == null) return null;
        String tpl = mediaProperties.getStreamServer().getPullUrlTemplateInternal();
        if (StrUtil.isBlank(tpl)) {
            tpl = mediaProperties.getStreamServer().getPullUrlTemplate();
        }
        if (StrUtil.isBlank(tpl)) {
            return live.getPullUrl();
        }
        String streamKey = buildStreamKey(live);
        String secret = extractSecret(live.getPushUrl());
        return tpl.replace("{streamKey}", streamKey)
                .replace("{streamkey}", streamKey)
                .replace("{secret}", secret);
    }

    /**
     * 根据FLV拉流地址转换为SRS WebRTC(WHEP)播放地址
     * FLV:  http://host:port/live/{sn}/{camera}.flv?secret=xxx
     * WHEP: http://host:port/rtc/v1/whep/?app=live/{sn}&stream={camera}.flv&secret=xxx
     */
    private String flvUrlToWhepUrl(String flvUrl) {
        if (StrUtil.isBlank(flvUrl)) return null;
        try {
            int qIdx = flvUrl.indexOf('?');
            String path = qIdx >= 0 ? flvUrl.substring(0, qIdx) : flvUrl;
            String query = qIdx >= 0 ? flvUrl.substring(qIdx + 1) : "";
            java.net.URI uri = new java.net.URI(path);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) port = 80;
            String uriPath = uri.getPath();
            // /live/{sn}/{camera}.flv → app=live/{sn}, stream={camera}.flv
            String app = "live";
            String stream = "";
            if (StrUtil.isNotBlank(uriPath)) {
                String[] parts = uriPath.split("/");
                // parts: ["", "live", "{sn}", "{camera}.flv"]
                if (parts.length >= 4) {
                    app = parts[1] + "/" + parts[2];  // live/{sn}
                    stream = parts[3];                   // {camera}.flv
                    for (int i = 4; i < parts.length; i++) stream += "/" + parts[i];
                } else if (parts.length == 3) {
                    app = parts[1];                       // live
                    stream = parts[2];                    // {camera}.flv
                } else if (parts.length == 2) {
                    stream = parts[1];
                }
            }
            StringBuilder whep = new StringBuilder("http://").append(host).append(":").append(port)
                    .append("/rtc/v1/whep/?app=").append(app).append("&stream=").append(stream);
            if (StrUtil.isNotBlank(query)) whep.append("&").append(query);
            return whep.toString();
        } catch (Exception e) {
            System.out.println("[FLV转WebRTC-异常❌] flvUrl=" + flvUrl + " err=" + e.getMessage());
            return null;
        }
    }

    private String buildAiRecognitionPullUrl(LiveStream live) {
        if (live == null) return null;
        String rtmpUrlAi = mediaProperties.getStreamServer().getRtmpUrlAi();
        if (StrUtil.isBlank(rtmpUrlAi)) return null;

        String streamKey = buildStreamKey(live);
        String secret = extractSecret(live.getPushUrl());

        String aiRtmp = rtmpUrlAi.replace("{streamKey}", streamKey).replace("{secret}", secret);

        try {
            int qIdx = aiRtmp.indexOf('?');
            String path = qIdx >= 0 ? aiRtmp.substring(0, qIdx) : aiRtmp;
            String query = qIdx >= 0 ? aiRtmp.substring(qIdx + 1) : null;

            java.net.URI uri = new java.net.URI(path);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) port = 1935;

            StringBuilder httpBase = new StringBuilder("http://").append(host).append(":2022");
            if (StrUtil.isNotBlank(uri.getPath())) httpBase.append(uri.getPath());
            if (!httpBase.toString().endsWith(".flv")) httpBase.append(".flv");
            if (StrUtil.isNotBlank(query)) httpBase.append('?').append(query);
            return httpBase.toString();
        } catch (Exception e) {
            String http = aiRtmp.replaceFirst("^rtmp://", "http://");
            int qIdx2 = http.indexOf('?');
            if (qIdx2 >= 0) return http.substring(0, qIdx2) + ".flv" + http.substring(qIdx2);
            return http + ".flv";
        }
    }

    @GetMapping("/lines")
    public R<List<LineDTO>> getLines() {
        String projectUuid = djiConfig.getDefaultProjectUuid();

        List<LineDTO> result = new java.util.ArrayList<>();

        try {
            List<Wayline> waylines = waylineService.listByProject(projectUuid);
            for (Wayline wayline : waylines) {
                LineDTO dto = new LineDTO();
                dto.setId(String.valueOf(wayline.getId()));
                dto.setFileName(wayline.getWaylineName() != null ? wayline.getWaylineName() : "");
                dto.setActionTypeFmt("航线任务");
                if (wayline.getUpdateTime() != null) {
                    dto.setModiTime(String.valueOf(wayline.getUpdateTime().toLocalDate().toEpochDay() * 86400000));
                }
                String uuid = wayline.getWaylineUuid();
                if (StrUtil.isBlank(uuid)) {
                    uuid = String.valueOf(wayline.getId());
                }
                dto.setWaylineUuid(uuid);
                dto.setDownloadUrl(wayline.getWpmlDownloadUrl());
                result.add(dto);
            }
        } catch (Exception e) {
            log.error("获取航线列表失败", e);
        }

        return R.ok(result);
    }

    @GetMapping("/alerts")
    public R<List<AlertDTO>> getAlerts() {
        // 读取全局数据源开关: video(视频识别) / image(图片识别)
        String dataSource = taskSwitchService.getAlertDataSource();
        boolean imageMode = "image".equalsIgnoreCase(dataSource);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if (imageMode) {
            // 图片识别模式: 查 zhimou_image_detection 表, 只返回已清洗的
            List<com.dji.sikong.entity.ZhimouImageDetection> imgResults = zhimouImageDetectionMapper.selectList(
                    new LambdaQueryWrapper<com.dji.sikong.entity.ZhimouImageDetection>()
                            .eq(com.dji.sikong.entity.ZhimouImageDetection::getDeleted, 0)
                            .ne(com.dji.sikong.entity.ZhimouImageDetection::getCleanStatus, 0)
                            .orderByDesc(com.dji.sikong.entity.ZhimouImageDetection::getEventTime)
                            .last("LIMIT 50")
            );
            List<AlertDTO> imgList = imgResults.stream().map(r -> {
                AlertDTO dto = new AlertDTO();
                dto.setId(String.valueOf(r.getId()));
                dto.setAppName("AI图片识别");
                dto.setRuleName(r.getAppName() != null ? r.getAppName() : "未知规则");
                dto.setAlarmTime(r.getEventTime() != null ? r.getEventTime().format(formatter) : "");
                dto.setLevel("high");
                if (r.getLocalImagePath() != null) {
                    dto.setImageUrl("/api/zhimou-img/" + r.getLocalImagePath());
                }
                if (r.getLongitude() != null) dto.setLng(r.getLongitude().doubleValue());
                if (r.getLatitude() != null) dto.setLat(r.getLatitude().doubleValue());
                return dto;
            }).collect(Collectors.toList());
            return R.ok(imgList);
        }

        // 视频识别模式(默认): 查 zhimou_detection_result 表
        List<ZhimouDetectionResult> results = detectionResultMapper.selectList(
                new LambdaQueryWrapper<ZhimouDetectionResult>()
                        .eq(ZhimouDetectionResult::getDeleted, 0)
                        .orderByDesc(ZhimouDetectionResult::getEventTime)
                        .last("LIMIT 50")
        );

        List<AlertDTO> result = results.stream().map(r -> {
            AlertDTO dto = new AlertDTO();
            dto.setId(String.valueOf(r.getId()));
            dto.setAppName("AI识别");
            dto.setRuleName(r.getRuleName() != null ? r.getRuleName() : "未知规则");
            dto.setAlarmTime(r.getEventTime() != null ? r.getEventTime().format(formatter) : "");
            dto.setLevel("high");
            if (r.getImageRelativePath() != null) {
                dto.setImageUrl("/api/zhimou-img/" + r.getImageRelativePath());
            }
            if (r.getLongitude() != null) dto.setLng(r.getLongitude().doubleValue());
            if (r.getLatitude() != null) dto.setLat(r.getLatitude().doubleValue());
            dto.setWaylineUuid(r.getWaylineUuid());
            return dto;
        }).collect(Collectors.toList());

        return R.ok(result);
    }

    @PostMapping("/devices/sync-names")
    public R<?> syncDeviceNames() {
        deviceSyncService.syncDeviceNamesFromOpenApi();
        return R.ok("设备名称同步完成");
    }

    @PostMapping("/alerts/fix-wayline-uuid")
    public R<?> fixWaylineUuid() {
        LambdaQueryWrapper<ZhimouDetectionResult> query = new LambdaQueryWrapper<>();
        query.eq(ZhimouDetectionResult::getDeleted, 0);
        query.eq(ZhimouDetectionResult::getWaylineUuid, "手动飞行");
        List<ZhimouDetectionResult> results = detectionResultMapper.selectList(query);
        
        int fixedCount = 0;
        int skipCount = 0;
        
        JsonNode openApiTasks = null;
        try {
            openApiTasks = openApiClient.listFlightTasks(djiConfig.getDefaultProjectUuid());
            log.info("[修复] 从OpenAPI获取飞行任务列表成功");
        } catch (Exception e) {
            log.warn("[修复] 从OpenAPI获取飞行任务列表失败: {}", e.getMessage());
        }
        
        for (ZhimouDetectionResult dr : results) {
            if (StrUtil.isBlank(dr.getAircraftSn())) {
                skipCount++;
                continue;
            }
            
            String waylineUuid = null;
            
            try {
                LambdaQueryWrapper<FlightTask> ftQuery = new LambdaQueryWrapper<>();
                ftQuery.eq(FlightTask::getAircraftSn, dr.getAircraftSn());
                ftQuery.eq(FlightTask::getDeleted, 0);
                ftQuery.le(FlightTask::getCreateTime, dr.getEventTime());
                ftQuery.orderByDesc(FlightTask::getId).last("LIMIT 1");
                List<FlightTask> tasks = flightTaskMapper.selectList(ftQuery);
                
                if (!tasks.isEmpty()) {
                    FlightTask task = tasks.get(0);
                    if (StrUtil.isNotBlank(task.getWaylineUuid())) {
                        waylineUuid = task.getWaylineUuid();
                    }
                }
            } catch (Exception e) {
                log.error("[修复] 查询flight_task失败", e);
            }
            
            if (StrUtil.isBlank(waylineUuid)) {
                try {
                    LambdaQueryWrapper<DeviceRealtimeStatus> statusQuery = new LambdaQueryWrapper<>();
                    statusQuery.eq(DeviceRealtimeStatus::getDeviceSn, dr.getAircraftSn());
                    statusQuery.le(DeviceRealtimeStatus::getUpdateTime, dr.getEventTime());
                    statusQuery.orderByDesc(DeviceRealtimeStatus::getId).last("LIMIT 1");
                    List<DeviceRealtimeStatus> statusList = realtimeStatusService.list(statusQuery);
                    
                    if (!statusList.isEmpty()) {
                        DeviceRealtimeStatus status = statusList.get(0);
                        if (StrUtil.isNotBlank(status.getWaylineUuid())) {
                            waylineUuid = status.getWaylineUuid();
                        } else if (StrUtil.isNotBlank(status.getCurrentTaskId())) {
                            LambdaQueryWrapper<FlightTask> ftQuery2 = new LambdaQueryWrapper<>();
                            ftQuery2.eq(FlightTask::getTaskUuid, status.getCurrentTaskId());
                            ftQuery2.eq(FlightTask::getDeleted, 0);
                            List<FlightTask> tasks = flightTaskMapper.selectList(ftQuery2);
                            
                            if (!tasks.isEmpty()) {
                                FlightTask task = tasks.get(0);
                                if (StrUtil.isNotBlank(task.getWaylineUuid())) {
                                    waylineUuid = task.getWaylineUuid();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[修复] 查询device_realtime_status失败", e);
                }
            }
            
            if (StrUtil.isBlank(waylineUuid)) {
                try {
                    Device aircraft = deviceService.getBySn(dr.getAircraftSn());
                    if (aircraft != null && StrUtil.isNotBlank(aircraft.getGatewaySn())) {
                        String airportSn = aircraft.getGatewaySn();
                        LambdaQueryWrapper<DeviceRealtimeStatus> airportStatusQuery = new LambdaQueryWrapper<>();
                        airportStatusQuery.eq(DeviceRealtimeStatus::getDeviceSn, airportSn);
                        airportStatusQuery.le(DeviceRealtimeStatus::getUpdateTime, dr.getEventTime());
                        airportStatusQuery.orderByDesc(DeviceRealtimeStatus::getId).last("LIMIT 1");
                        List<DeviceRealtimeStatus> airportStatusList = realtimeStatusService.list(airportStatusQuery);
                        
                        if (!airportStatusList.isEmpty()) {
                            DeviceRealtimeStatus airportStatus = airportStatusList.get(0);
                            if (StrUtil.isNotBlank(airportStatus.getWaylineUuid())) {
                                waylineUuid = airportStatus.getWaylineUuid();
                                log.info("[修复] 从绑定机场关联航线UUID: 检测结果ID={}, aircraftSn={}, airportSn={}, waylineUuid={}", 
                                        dr.getId(), dr.getAircraftSn(), airportSn, waylineUuid);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[修复] 查询机场device_realtime_status失败", e);
                }
            }
            
            if (StrUtil.isBlank(waylineUuid) && openApiTasks != null) {
                try {
                    if (openApiTasks.has("data") && openApiTasks.get("data").has("list")) {
                        JsonNode listNode = openApiTasks.get("data").get("list");
                        if (listNode.isArray()) {
                            for (JsonNode taskNode : listNode) {
                                String taskDeviceSn = taskNode.has("device_sn") ? taskNode.get("device_sn").asText(null) : null;
                                if (dr.getAircraftSn().equals(taskDeviceSn)) {
                                    String wUuid = taskNode.has("wayline_uuid") ? taskNode.get("wayline_uuid").asText(null) : null;
                                    if (StrUtil.isNotBlank(wUuid)) {
                                        waylineUuid = wUuid;
                                        log.info("[修复] 从OpenAPI关联航线UUID: 检测结果ID={}, aircraftSn={}, waylineUuid={}", 
                                                dr.getId(), dr.getAircraftSn(), waylineUuid);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[修复] OpenAPI查询航线UUID失败", e);
                }
            }
            
            if (StrUtil.isNotBlank(waylineUuid)) {
                dr.setWaylineUuid(waylineUuid);
                detectionResultMapper.updateById(dr);
                fixedCount++;
                log.info("[修复] 检测结果ID={}, aircraftSn={}, 关联航线UUID={}", 
                        dr.getId(), dr.getAircraftSn(), waylineUuid);
            } else {
                skipCount++;
            }
        }
        
        return R.ok("修复完成: 修复" + fixedCount + "条, 保持手动飞行" + skipCount + "条(无对应任务)");
    }

    /**
     * 判断请求是否来自内网
     * 内网IP段: 10.x.x.x, 172.16-31.x.x, 192.168.x.x, 127.x.x.x
     */
    private boolean isInternalRequest(HttpServletRequest request) {
        if (request == null) return true;
        String ip = getClientIp(request);
        if (StrUtil.isBlank(ip)) return true;
        return isPrivateIp(ip);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) return ip.trim();
        return request.getRemoteAddr();
    }

    private boolean isPrivateIp(String ip) {
        try {
            if ("127.0.0.1".equals(ip) || "localhost".equalsIgnoreCase(ip)) return true;
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            int p1 = Integer.parseInt(parts[0]);
            int p2 = Integer.parseInt(parts[1]);
            // 10.x.x.x
            if (p1 == 10) return true;
            // 172.16-31.x.x
            if (p1 == 172 && p2 >= 16 && p2 <= 31) return true;
            // 192.168.x.x
            if (p1 == 192 && p2 == 168) return true;
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private String getModeText(Integer mode) {
        switch (mode) {
            case 0: return "待机";
            case 1: return "起飞准备";
            case 2: return "起飞准备完毕";
            case 3: return "手动飞行";
            case 4: return "自动起飞";
            case 5: return "航线飞行";
            case 6: return "全景拍照";
            case 7: return "智能跟随";
            case 8: return "ADS-B 躲避";
            case 9: return "自动返航";
            case 10: return "自动降落";
            case 11: return "强制降落";
            case 12: return "三桨叶降落";
            case 13: return "升级中";
            case 14: return "未连接";
            case 15: return "APAS";
            case 16: return "虚拟摇杆状态";
            case 17: return "指令飞行";
            case 18: return "空中 RTK 收敛模式";
            case 19: return "机场选址中";
            case 20: return "POI环绕";
            case 21: return "进离场航线飞行过程中";
            default: return "未定状态";
        }
    }

    @GetMapping("/debug/config")
    public R<Map<String, String>> debugConfig() {
        Map<String, String> config = new java.util.HashMap<>();
        config.put("orgUuid", String.valueOf(djiConfig.getDefaultOrganizationUuid()));
        config.put("projectUuid", String.valueOf(djiConfig.getDefaultProjectUuid()));
        config.put("orgUuidBlank", String.valueOf(cn.hutool.core.util.StrUtil.isNotBlank(djiConfig.getDefaultOrganizationUuid())));
        config.put("projectUuidBlank", String.valueOf(cn.hutool.core.util.StrUtil.isNotBlank(djiConfig.getDefaultProjectUuid())));
        return R.ok(config);
    }
}
