package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.common.RedisKeyConstants;
import com.dji.sikong.config.DjiMediaProperties;
import com.dji.sikong.config.DjiOpenApiProperties;
import com.dji.sikong.config.ZhimouProperties;
import com.dji.sikong.dto.StartLiveStreamDTO;
import com.dji.sikong.entity.LiveStream;
import com.dji.sikong.mapper.LiveStreamMapper;
import com.dji.sikong.mqtt.DjiMqttClient;
import com.dji.sikong.openapi.DjiOpenApiClient;
import com.dji.sikong.service.LiveStreamService;
import com.dji.sikong.service.ZhimouTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LiveStreamServiceImpl extends ServiceImpl<LiveStreamMapper, LiveStream> implements LiveStreamService {

    @Resource
    private LiveStreamMapper liveStreamMapper;

    @Resource
    private DjiMediaProperties mediaProperties;

    @Resource
    private DjiOpenApiClient openApiClient;

    @Resource
    private DjiMqttClient mqttClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DjiOpenApiProperties openApiProperties;

    @Resource
    private ZhimouTaskService zhimouTaskService;

    @Resource
    private ZhimouProperties zhimouProperties;

    @Resource
    private com.dji.sikong.mapper.DeviceMapper deviceMapper;

    @Override
    public LiveStream startByPassLive(StartLiveStreamDTO dto) {
        // ========== 新增防线:项目级隔离 ==========
        // 只允许默认项目(5ac8e7fe-210e-4ad0-95db-c59c67bea827)下的飞机/网关创建直播
        // 防止同一组织下其他项目的飞机被错误创建直播和知眸任务
        String defaultProjectUuid = openApiProperties.getDefaultProjectUuid();
        String reqProjectUuid = dto.getProjectUuid();
        String acSn = dto.getAircraftSn();
        String gwSn = dto.getGatewaySn();
        java.util.List<String> checkSns = new java.util.ArrayList<>();
        if (StrUtil.isNotBlank(acSn)) checkSns.add(acSn);
        if (StrUtil.isNotBlank(gwSn) && !gwSn.equals(acSn)) checkSns.add(gwSn);
        boolean projectMatch = false;
        if (StrUtil.isNotBlank(defaultProjectUuid)) {
            // 1. 如果前端传了projectUuid,必须等于默认值,否则直接拒绝
            if (StrUtil.isNotBlank(reqProjectUuid) && !defaultProjectUuid.equals(reqProjectUuid)) {
                System.out.println("[项目过滤❌开播拒绝] 前端传入projectUuid=" + reqProjectUuid
                        + " ≠ 默认项目=" + defaultProjectUuid + " → 拒绝创建直播(aircraftSn=" + acSn + " gatewaySn=" + gwSn + ")");
                return null;
            }
            // 2. 查device表:所有SN(飞机+网关)对应的projectUuid必须都等于默认值,只要有一个不匹配就拒绝
            for (String sn : checkSns) {
                try {
                    LambdaQueryWrapper<com.dji.sikong.entity.Device> qw = new LambdaQueryWrapper<>();
                    qw.eq(com.dji.sikong.entity.Device::getSn, sn).last(" LIMIT 1");
                    com.dji.sikong.entity.Device d = deviceMapper.selectOne(qw);
                    if (d != null && defaultProjectUuid.equals(d.getProjectUuid())) {
                        System.out.println("[项目过滤✅开播放行] sn=" + sn + " projectUuid=" + d.getProjectUuid() + " = 默认项目");
                    } else if (d == null) {
                        System.out.println("[项目过滤❌开播拒绝] sn=" + sn + " device表中不存在(未同步?) → 拒绝创建直播");
                        return null;
                    } else {
                        System.out.println("[项目过滤❌开播拒绝] sn=" + sn + " 所属projectUuid=" + d.getProjectUuid()
                                + " ≠ 默认项目=" + defaultProjectUuid + " → 拒绝创建直播(防止跨项目飞机创建任务)");
                        return null;
                    }
                } catch (Exception e) {
                    System.out.println("[项目过滤❌开播拒绝] 查询device表异常 sn=" + sn + " err=" + e.getMessage() + " → 拒绝创建直播");
                    return null;
                }
            }
        } else {
            System.out.println("[项目过滤⚠️开播] openApiProperties.defaultProjectUuid为空(yml未配置) → 不启用项目隔离,全部放行");
        }

        // ========== 【Bug3修复】: 拦截网关设备(aircraft_sn==gateway_sn且device_type=1/网关)创建camera_index=0的无效直播 ==========
        // 这些是网关自己的OSD错误触发的"虚假飞机直播", 每次OSD新建1条, 必须严格拦截!
        if (StrUtil.isNotBlank(acSn) && StrUtil.isNotBlank(gwSn) && acSn.equals(gwSn)) {
            // 查device表确认是不是网关(device_type=1) / 机场(4) / 遥控器(2)
            boolean isNotAircraft = false;
            try {
                LambdaQueryWrapper<com.dji.sikong.entity.Device> qw = new LambdaQueryWrapper<>();
                qw.eq(com.dji.sikong.entity.Device::getSn, acSn).last(" LIMIT 1");
                com.dji.sikong.entity.Device d = deviceMapper.selectOne(qw);
                if (d != null && d.getDeviceType() != null) {
                    int t = d.getDeviceType().intValue();
                    if (t != 3) { // device_type != 3(飞机)就是非飞机设备
                        isNotAircraft = true;
                        System.out.println("[无效直播拦截🚫开播] aircraftSn=gatewaySn=" + acSn + " device_type=" + t + "(1=网关/2=遥控/4=机场,≠3飞机) → 拒绝创建camera_index=" + dto.getCameraIndex() + "的无效直播(防止每次OSD新建1条!)");
                    }
                }
            } catch (Exception e) {
                System.out.println("[无效直播拦截🔍开播] 查询device表异常 sn=" + acSn + " err=" + e.getMessage());
            }
            // 如果camera_index还是默认"0" → 100%是OSD自动触发的网关假直播,强行拦截
            if ("0".equals(dto.getCameraIndex()) && isNotAircraft) {
                return null;
            }
        }

        // ========== 【Bug3b修复】: startByPassLive创建直播前先查同aircraft_sn下是否已有活跃直播 → 只有status=1(真正运行中)才复用,status=0(待启动)是脏数据直接删掉重来 ==========
        if (StrUtil.isNotBlank(acSn)) {
            try {
                LambdaQueryWrapper<LiveStream> qwActive = new LambdaQueryWrapper<>();
                qwActive.eq(LiveStream::getAircraftSn, acSn)
                        .eq(LiveStream::getDeleted, 0)
                        .in(LiveStream::getStatus, 0, 1)
                        .orderByDesc(LiveStream::getId)
                        .last(" LIMIT 1");
                LiveStream active = liveStreamMapper.selectOne(qwActive);
                if (active != null) {
                    if (active.getStatus() != null && active.getStatus().intValue() == 1) {
                        System.out.println("[活跃直播复用✅开播] aircraftSn=" + acSn + " 已有活跃直播liveId=" + active.getId()
                                + " status=1(运行中) → 直接复用不重复创建");
                        return active;
                    } else {
                        // status=0(待启动)或其他非运行状态 → 是脏数据, 删除后重新创建
                        System.out.println("[活跃直播复用-清理🗑️开播] aircraftSn=" + acSn + " 存在旧直播liveId=" + active.getId()
                                + " status=" + active.getStatus() + "(非运行中,是脏数据) → 删除后重新走开播流程");
                        try {
                            active.setDeleted(1);
                            liveStreamMapper.updateById(active);
                        } catch (Exception delEx) {
                            System.out.println("[活跃直播复用-清理-异常⚠️] 删除旧直播liveId=" + active.getId() + "失败: " + delEx.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[活跃直播复用🔍开播] 查询同aircraft_sn活跃直播异常 sn=" + acSn + " err=" + e.getMessage() + " → 继续走创建流程");
            }
        }

        LiveStream live = new LiveStream();
        live.setStreamName(StrUtil.isNotBlank(dto.getStreamName()) ? dto.getStreamName() : "live-" + System.currentTimeMillis());
        live.setGatewaySn(dto.getGatewaySn());
        live.setAircraftSn(dto.getAircraftSn());
        live.setCameraIndex(dto.getCameraIndex());
        live.setPushType(1);
        live.setStreamProtocol(dto.getStreamProtocol() == null ? "rtmp" : dto.getStreamProtocol());
        live.setQualityType(dto.getQualityType() == null ? "adaptive" : dto.getQualityType());
        live.setVideoExpire(dto.getVideoExpire() == null ? 7200 : dto.getVideoExpire());
        live.setStatus(0);
        liveStreamMapper.insert(live);

        // ============== 生成动态推流地址(按设备SN+镜头编号区分, 保留secret鉴权) ==============
        String pushUrl = dto.getPushUrl();
        if (StrUtil.isBlank(pushUrl)) {
            String streamKey = buildStreamKey(dto, live);
            // 优先使用公网推流地址(司空2与SRS不在同一内网), 公网未配置则用内网
            String rtmpTpl = mediaProperties.getStreamServer().getRtmpUrl();
            if (StrUtil.isBlank(rtmpTpl)) {
                rtmpTpl = mediaProperties.getStreamServer().getRtmpUrlInternal();
            }
            pushUrl = buildDynamicPushUrl(rtmpTpl, streamKey);
            // 设置拉流地址(给前端播放)
            String pullUrl = buildDynamicPullUrl(mediaProperties.getStreamServer(), pushUrl, streamKey);
            if (StrUtil.isNotBlank(pullUrl)) live.setPullUrl(pullUrl);
        }
        live.setPushUrl(pushUrl);
        System.out.println("[直播推流地址生成]"
                + " streamName=" + live.getStreamName()
                + " gatewaySn=" + dto.getGatewaySn()
                + " aircraftSn=" + dto.getAircraftSn()
                + " cameraIndex=" + dto.getCameraIndex()
                + "\n  pushUrl=" + live.getPushUrl()
                + "\n  pullUrl=" + live.getPullUrl());

        try {
            String tid = UUID.randomUUID().toString().replace("-", "");
            String bid = UUID.randomUUID().toString().replace("-", "");

            // ========== 旁路推流方式：创建码流转发器 ==========
            // 优先使用传入的projectUuid，否则使用配置中的默认值
            String projectUuid = StrUtil.isNotBlank(dto.getProjectUuid()) ? dto.getProjectUuid()
                    : openApiProperties.getDefaultProjectUuid();

            // 尝试司空2 OpenAPI 创建/更新码流转发器
            Map<String, Object> body = new HashMap<>();
            String deviceSn = StrUtil.isNotBlank(dto.getAircraftSn()) ? dto.getAircraftSn() : dto.getGatewaySn();
            String camIndex = StrUtil.isNotBlank(dto.getCameraIndex()) ? dto.getCameraIndex() : "0";
            body.put("sn", deviceSn);
            body.put("camera", camIndex);
            body.put("video", "normal-0");
            body.put("schema", StrUtil.isNotBlank(live.getStreamProtocol()) ? live.getStreamProtocol() : "rtmp");
            body.put("converter_name", StrUtil.isNotBlank(dto.getStreamName()) ? dto.getStreamName() : live.getStreamName());
            body.put("quality_type", live.getQualityType());
            body.put("video_expire", live.getVideoExpire());
            body.put("auto_push_stream", true);
            Map<String, Object> bypassOption = new HashMap<>();
            bypassOption.put("url", pushUrl);
            body.put("bypass_option", bypassOption);
            // 策略: 先 listStreamConverters 查 sn+camera 是否已有converter → 有就PUT(auto_push=True) → 没有才POST创建
            JsonNode resp = null;
            boolean triedPut = false;
            try {
                JsonNode listResp = openApiClient.listStreamConverters(projectUuid);
                if (listResp != null && listResp.has("data") && listResp.get("data").has("list")) {
                    for (JsonNode c : listResp.get("data").get("list")) {
                        String cSn = c.has("sn") ? c.get("sn").asText("") : "";
                        String cCam = c.has("camera") ? c.get("camera").asText("") : "";
                        if (deviceSn.equals(cSn) && camIndex.equals(cCam)) {
                            String cid = c.has("converter_id") ? c.get("converter_id").asText("") : "";
                            if (StrUtil.isNotBlank(cid)) {
                                body.put("converter_id", cid);
                                triedPut = true;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("查询码流转发器列表异常, 回退到创建流程: " + e.getMessage());
            }
            // body(含converter_id) 序列化入库
            live.setRequestJson(JSONUtil.toJsonStr(body));
            // 如果匹配到converter则走PUT, 否则走POST创建
            resp = triedPut
                    ? openApiClient.updateStreamConverter(projectUuid, body.get("converter_id").toString(), body)
                    : openApiClient.createStreamConverter(projectUuid, body);
            // 如果PUT异常/返回空, 回退到POST
            if (triedPut && resp == null) {
                resp = openApiClient.createStreamConverter(projectUuid, body);
                triedPut = false;
            }

            if (resp != null) {
                live.setResponseJson(resp.toString());
                if (resp.has("data")) {
                    JsonNode data = resp.get("data");
                    if (data.has("converter_id")) live.setStreamId(data.get("converter_id").asText());
                    else if (data.has("id")) live.setStreamId(data.get("id").asText());
                    if (data.has("bypass_option")) {
                        JsonNode bo = data.get("bypass_option");
                        if (bo.has("url")) live.setPushUrl(bo.get("url").asText());
                    }
                    if (data.has("push_url")) live.setPushUrl(data.get("push_url").asText());
                    if (data.has("pull_url")) live.setPullUrl(data.get("pull_url").asText());
                    if (data.has("url_type")) live.setUrlType(data.get("url_type").asText());
                }
                // 司空2 OpenAPI成功返回code=0 (不是200)
                if (resp.has("code") && (resp.get("code").asInt() == 0 || resp.get("code").asInt() == 200)) {
                    live.setStatus(1);
                    live.setStartTime(LocalDateTime.now());
                    live.setExpireTime(LocalDateTime.now().plusSeconds(live.getVideoExpire()));
                    live.setStatusMessage(triedPut
                            ? "司空2已更新码流转发器(auto_push=True)开启旁路推流"
                            : "司空2已创建转发通道并开启推流");
                } else {
                    // 如果OpenAPI失败，尝试MQTT services方式(live_start_push)
                    live = fallbackMqtt(dto, live, tid, bid, pushUrl);
                }
            } else {
                live = fallbackMqtt(dto, live, tid, bid, pushUrl);
            }

        } catch (Exception e) {
            live.setStatus(3);
            live.setStatusMessage("启动异常: " + e.getMessage());
        }
        liveStreamMapper.updateById(live);

        // ================ 知眸AI: 开码流成功后 → 写入3秒延迟队列, 到点后由2秒统一调度器创建(无任何拦截,空参数也照样入队,让知眸API返回真实错误) ================
        try {
            String srcDtoApp = dto.getZhimouAppId();
            String srcDbApp = live.getZhimouAppId();
            String srcYmlApp = zhimouProperties.getOpenapi().getDefaultAppId();
            String srcDtoPkg = dto.getZhimouPkgId();
            String srcDbPkg = live.getZhimouPkgId();
            String srcYmlPkg = zhimouProperties.getOpenapi().getDefaultPkgId();
            String rawAppId = StrUtil.isNotBlank(srcDtoApp) ? srcDtoApp
                    : (StrUtil.isNotBlank(srcDbApp) ? srcDbApp : srcYmlApp);
            String rawPkgId = StrUtil.isNotBlank(srcDtoPkg) ? srcDtoPkg
                    : (StrUtil.isNotBlank(srcDbPkg) ? srcDbPkg : srcYmlPkg);
            // 逗号分隔的多appId → 保留完整字符串, 由下游createVideoTaskWhenLiveStart解析
            String appId = (rawAppId == null) ? null : rawAppId.trim();
            String pkgId = (rawPkgId == null) ? null : rawPkgId.trim();
            String srcApp = StrUtil.isNotBlank(srcDtoApp) ? "前端dto"
                    : (StrUtil.isNotBlank(srcDbApp) ? "DB旧live记录" : "yml默认配置");
            String srcPkg = StrUtil.isNotBlank(srcDtoPkg) ? "前端dto"
                    : (StrUtil.isNotBlank(srcDbPkg) ? "DB旧live记录" : "yml默认配置");
            String sn = StrUtil.isNotBlank(live.getAircraftSn()) ? live.getAircraftSn() : live.getGatewaySn();
            // ========================================
            // ✈️ 只有真正的飞机码流才调用知眸!
            //    机场码流(aircraftSn空/等于gatewaySn) + 测试数据(TEST-开头)直接不调用
            // ========================================
            String asn = live.getAircraftSn(), gsn = live.getGatewaySn();
            boolean realAircraft = StrUtil.isNotBlank(asn) && !asn.equals(gsn) && !asn.startsWith("TEST-");
            if (!realAircraft) {
                System.out.println("[知眸-删除🚫开任务] liveId=" + live.getId() + " 不是真正飞机码流 asn=[" + asn + "] gsn=[" + gsn + "] → 不调scheduleTaskOpen(删除非飞机逻辑)");
            } else {
                System.out.println("[知眸-绑定开播-入队🟢] liveId=" + live.getId()
                        + " aircraftSn=" + sn
                        + " live.status=" + live.getStatus()
                        + " rawAppId=[" + rawAppId + "]" + (rawAppId!=null && rawAppId.contains(",") ? "(多app逗号分隔)" : "") + " → appId=[" + appId + "]" + "(来源=" + srcApp + "|dto=" + srcDtoApp + "|db=" + srcDbApp + "|yml=" + srcYmlApp + ")"
                        + " pkgId=[" + pkgId + "]" + "(来源=" + srcPkg + "|dto=" + srcDtoPkg + "|db=" + srcDbPkg + "|yml=" + srcYmlPkg + ")");
                zhimouTaskService.scheduleTaskOpenAfterLiveStart(live, appId, pkgId);
            }
        } catch (Exception e) {
            log.warn("[知眸-绑定开播] 入3秒延迟队列异常(不影响直播本身): " + e.getMessage(), e);
            System.out.println("[知眸-绑定开播-异常❌] liveId=" + live.getId()
                    + " aircraftSn=" + (live.getAircraftSn() != null ? live.getAircraftSn() : live.getGatewaySn())
                    + " 异常: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }

        return live;
    }

    private LiveStream fallbackMqtt(StartLiveStreamDTO dto, LiveStream live, String tid, String bid, String pushUrl) {
        try {
            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("tid", tid);
            msgBody.put("bid", bid);
            msgBody.put("timestamp", System.currentTimeMillis());
            msgBody.put("gateway", StrUtil.isNotBlank(dto.getGatewaySn()) ? dto.getGatewaySn() : dto.getAircraftSn());
            msgBody.put("method", "live_start_push");
            Map<String, Object> data = new HashMap<>();
            data.put("sn", StrUtil.isNotBlank(dto.getAircraftSn()) ? dto.getAircraftSn() : dto.getGatewaySn());
            data.put("camera_index", dto.getCameraIndex());
            data.put("url_type", mediaProperties.getStreamServer().getType());
            data.put("quality_type", live.getQualityType());
            data.put("video_expire", live.getVideoExpire());
            Map<String, Object> urlMap = new HashMap<>();
            urlMap.put("rtmp_url", pushUrl);
            data.put("url", urlMap);
            msgBody.put("data", data);

            String msg = JSONUtil.toJsonStr(msgBody);
            boolean published = false;
            String lastTopic = null;
            java.util.HashSet<String> topics = new java.util.HashSet<>();
            // 机场SN(如果与飞机SN不同则加)
            if (StrUtil.isNotBlank(dto.getGatewaySn())) topics.add("thing/product/" + dto.getGatewaySn() + "/services");
            // 飞机SN(如果与机场SN不同也加上)
            if (StrUtil.isNotBlank(dto.getAircraftSn())) topics.add("thing/product/" + dto.getAircraftSn() + "/services");
            for (String topic : topics) {
                boolean ok = mqttClient.publish(topic, msg, 1);
                if (ok) { published = true; lastTopic = topic; }
                System.out.println("[直播MQTT下发 " + (ok ? "✅成功" : "❌失败")
                        + " topic=" + topic
                        + " method=live_start_push"
                        + " sn=" + dto.getAircraftSn() + "/" + dto.getGatewaySn());
            }
            if (published) {
                stringRedisTemplate.opsForValue().set(
                        RedisKeyConstants.LIVE_TID_BID_KEY + tid,
                        String.valueOf(live.getId()),
                        RedisKeyConstants.TID_MATCH_EXPIRE_SECONDS, TimeUnit.SECONDS);
                live.setStatus(0);
                if (StrUtil.isBlank(live.getStatusMessage())) {
                    live.setStatusMessage("MQTT已发送(topic="
                            + (lastTopic != null ? lastTopic : "")
                            + "). 等待设备回复");
                }
            } else {
                live.setStatus(3);
                live.setStatusMessage("旁路推流及MQTT下发失败");
            }
        } catch (Exception e) {
            live.setStatus(3);
            live.setStatusMessage("fallback失败: " + e.getMessage());
        }
        return live;
    }

    @Override
    public LiveStream getByStreamId(String streamId) {
        if (StrUtil.isBlank(streamId)) return null;
        return liveStreamMapper.selectOne(
                new LambdaQueryWrapper<LiveStream>()
                        .and(w -> w.eq(LiveStream::getStreamId, streamId).or().eq(LiveStream::getId, Long.parseLong(streamId)))
                        .eq(LiveStream::getDeleted, 0));
    }

    // ========== 动态推流/拉流地址生成工具 ==========

    /**
     * 根据前端传的 {gatewaySn,aircraftSn,cameraIndex} 拼出区分每台设备的流标识:
     *   有 aircraftSn + cameraIndex → {aircraftSn}/{cameraIndex}  (例: 1581F8HGX252F00A018X/99-0-0)
     *   只有 aircraftSn          → {aircraftSn}
     *   只有 gatewaySn           → {gatewaySn}                    (例: 8UUXN2A00A00M9)
     *   都没有则 fallback 到 live-{id}
     */
    private String buildStreamKey(StartLiveStreamDTO dto, LiveStream live) {
        StringBuilder sb = new StringBuilder();
        String sn = StrUtil.isNotBlank(dto.getAircraftSn()) ? dto.getAircraftSn() : dto.getGatewaySn();
        if (StrUtil.isNotBlank(sn)) sb.append(sn);
        else sb.append("live-").append(live.getId());
        if (StrUtil.isNotBlank(dto.getCameraIndex())) sb.append('/').append(dto.getCameraIndex());
        return sb.toString();
    }

    /**
     * 把yml中形如: rtmp://58.213.86.2/live?secret=f91c3c6060cd401b99ae649fc50a9bb6
     * 的地址, 拼上 streamKey 变:  rtmp://58.213.86.2/live/{streamKey}?secret=xxx
     * (若yml中旧格式为 .../live/livestream?secret=xxx 也兼容: 会取到 /live 前半段做app)
     */
    private String buildDynamicPushUrl(String rtmpTemplate, String streamKey) {
        if (StrUtil.isBlank(rtmpTemplate)) {
            return "rtmp://127.0.0.1/live/" + streamKey;
        }
        // 1. 拆分 query 参数(?secret=xxx)
        String baseNoQuery;
        String query = null;
        int q = rtmpTemplate.indexOf('?');
        if (q >= 0) {
            baseNoQuery = rtmpTemplate.substring(0, q);
            query = rtmpTemplate.substring(q + 1);
        } else {
            baseNoQuery = rtmpTemplate;
        }
        if (baseNoQuery.endsWith("/")) baseNoQuery = baseNoQuery.substring(0, baseNoQuery.length() - 1);

        // 2. 截取到 app 名(默认/live):  找最后一个 "/live" 作为截断点
        String appPrefix = findAppPrefix(baseNoQuery, "/live");

        // 3. 拼接:  prefix/app/streamKey?query
        StringBuilder sb = new StringBuilder(appPrefix).append('/').append(streamKey);
        if (StrUtil.isNotBlank(query)) sb.append('?').append(query);
        return sb.toString();
    }

    /**
     * 生成前端播放用的拉流地址. 优先级:
     *   1) yml中配置的 pull-url-template (支持 {streamKey}/{secret} 占位符)
     *   2) 从 pushUrl 推导:  rtmp://host/app/streamKey?secret=xxx → http://host/app/streamKey.flv?secret=xxx
     *                          (端口号保持原样, 若是默认1935端口未显式写则不替换)
     */
    private String buildDynamicPullUrl(DjiMediaProperties.StreamServer streamServer,
                                       String pushUrl, String streamKey) {
        String tpl = streamServer.getPullUrlTemplate();
        if (StrUtil.isNotBlank(tpl)) {
            String secret = "";
            int q = pushUrl.indexOf('?');
            if (q >= 0) {
                String qs = pushUrl.substring(q + 1);
                for (String pair : qs.split("&")) {
                    int eq = pair.indexOf('=');
                    String k = eq >= 0 ? pair.substring(0, eq) : pair;
                    if ("secret".equalsIgnoreCase(k)) {
                        secret = eq >= 0 ? pair.substring(eq + 1) : "";
                        break;
                    }
                }
            }
            return tpl.replace("{streamKey}", streamKey)
                    .replace("{streamkey}", streamKey)
                    .replace("{secret}", secret);
        }
        // 模板未配置: 从 pushUrl 自动推导 HTTP-FLV 拉流地址
        if (StrUtil.isBlank(pushUrl)) return null;
        try {
            // 先去掉 ?query
            String path, query = null;
            int qIdx = pushUrl.indexOf('?');
            if (qIdx >= 0) { path = pushUrl.substring(0, qIdx); query = pushUrl.substring(qIdx + 1); }
            else path = pushUrl;
            // 把 rtmp://xxx → http://xxx
            URI uri = new URI(path);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) port = 1935;        // RTMP默认端口, HTTP-FLV一般复用同端口
            StringBuilder httpBase = new StringBuilder("http://").append(host).append(':').append(port);
            if (StrUtil.isNotBlank(uri.getPath())) httpBase.append(uri.getPath());
            // 末尾 .flv
            if (!httpBase.toString().endsWith(".flv")) httpBase.append(".flv");
            if (StrUtil.isNotBlank(query)) httpBase.append('?').append(query);
            return httpBase.toString();
        } catch (URISyntaxException e) {
            // 解析失败则粗暴替换
            String http = pushUrl.replaceFirst("^rtmp://", "http://");
            int qIdx = http.indexOf('?');
            if (qIdx >= 0) return http.substring(0, qIdx) + ".flv" + http.substring(qIdx);
            return http + ".flv";
        }
    }

    /**
     * 从 noQuery URL 里截取到 "{appName}" 之前(包含 appName) 的前缀:
     *   rtmp://58.213.86.2/live           → rtmp://58.213.86.2/live
     *   rtmp://58.213.86.2/live/livestream → rtmp://58.213.86.2/live   (兼容老配置)
     *   rtmp://host:1935/live/xxx          → rtmp://host:1935/live
     *   rtmp://host/some/other             → rtmp://host/some/other    (找不到默认 appName 就按原最后一级前截取)
     */
    private static String findAppPrefix(String noQuery, String defaultAppName) {
        // 先尝试精确匹配包含 /live 的位置（作为 app 名的分界）
        int idx = noQuery.lastIndexOf(defaultAppName);
        if (idx > 0) {
            return noQuery.substring(0, idx + defaultAppName.length());
        }
        // 没有默认 /live, 取到最后一个 "/" 之前(去掉最后一段流名占位)
        int last = noQuery.lastIndexOf('/');
        if (last > 10) return noQuery.substring(0, last);   // >10 过滤掉 rtmp:// 里不存在 "/" 的情况
        return noQuery;
    }

    // ========== 自动开关直播（OSD高度变化触发） ==========

    @Override
    public boolean hasActiveLive(String aircraftSn) {
        if (StrUtil.isBlank(aircraftSn)) return false;
        Long cnt = liveStreamMapper.selectCount(new LambdaQueryWrapper<LiveStream>()
                .eq(LiveStream::getAircraftSn, aircraftSn)
                .eq(LiveStream::getDeleted, 0)
                .eq(LiveStream::getStatus, 1)); // 只算真正在推送中的(status=1)，status=0(待回复)/2(已停)/3(失败)都不算活跃
        return cnt != null && cnt > 0;
    }

    @Override
    public boolean syncAndHasActiveLive(String aircraftSn) {
        // ⚠️⚠️⚠️ 本方法是【只读不写】的！绝不修改 live_stream 表的任何字段！
        // ⚠️旧版本Bug：司空2 listStreamConverters GET接口有状态同步延迟，
        //    PUT auto_push=true成功后立即GET仍可能返回旧的auto_push=false → 旧代码会把本地status=1误改回2 → 健康检查误关知眸任务→死循环
        // ⚠️修复后：只读返回状态，不碰DB，把"改status=1→2"的权力完全交给显式调用 stopByAircraftSn() 的代码路径
        if (StrUtil.isBlank(aircraftSn)) return false;
        // 1. 先查本地DB（只读）
        LiveStream active = liveStreamMapper.selectOne(new LambdaQueryWrapper<LiveStream>()
                .eq(LiveStream::getAircraftSn, aircraftSn)
                .eq(LiveStream::getDeleted, 0)
                .in(LiveStream::getStatus, 0, 1, 3)
                .orderByDesc(LiveStream::getId)
                .last(" LIMIT 1"));
        if (active == null) return false;

        // 2. 查司空2 OpenAPI 真实码流转发器状态（只读，绝不改本地DB！）
        String projectUuid = openApiProperties.getDefaultProjectUuid();
        try {
            JsonNode listResp = openApiClient.listStreamConverters(projectUuid);
            if (listResp != null && listResp.has("data") && listResp.get("data").has("list")) {
                String camIndex = StrUtil.isNotBlank(active.getCameraIndex()) ? active.getCameraIndex() : "0";
                for (JsonNode c : listResp.get("data").get("list")) {
                    String cSn = c.has("sn") ? c.get("sn").asText("") : "";
                    String cCam = c.has("camera") ? c.get("camera").asText("") : "";
                    if (aircraftSn.equals(cSn) && camIndex.equals(cCam)) {
                        boolean autoPush = c.has("auto_push_stream") && c.get("auto_push_stream").asBoolean(false);
                        return autoPush;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[状态同步-只读] 查询司空2码流状态失败 aircraftSn=" + aircraftSn + ", 回退使用本地DB状态: " + e.getMessage());
        }
        // 3. 查不到司空2数据(离线/无converter)→回退使用本地DB的status=1判断
        return active.getStatus() == 1;
    }

    @Override
    public boolean stopStreamConverterOnly(String aircraftSn) {
        if (StrUtil.isBlank(aircraftSn)) return false;
        LiveStream live = liveStreamMapper.selectOne(new LambdaQueryWrapper<LiveStream>()
                .eq(LiveStream::getAircraftSn, aircraftSn)
                .eq(LiveStream::getDeleted, 0)
                .in(LiveStream::getStatus, 0, 1)
                .orderByDesc(LiveStream::getCreateTime)
                .last(" LIMIT 1"));
        if (live == null) {
            live = liveStreamMapper.selectOne(new LambdaQueryWrapper<LiveStream>()
                    .eq(LiveStream::getGatewaySn, aircraftSn)
                    .eq(LiveStream::getDeleted, 0)
                    .in(LiveStream::getStatus, 0, 1)
                    .orderByDesc(LiveStream::getCreateTime)
                    .last(" LIMIT 1"));
        }
        if (live == null) return false;
        try {
            String projectUuid = openApiProperties.getDefaultProjectUuid();
            String converterId = live.getStreamId();
            if (StrUtil.isBlank(converterId)) {
                try {
                    JsonNode listResp = openApiClient.listStreamConverters(projectUuid);
                    String cam = StrUtil.isNotBlank(live.getCameraIndex()) ? live.getCameraIndex() : "0";
                    String devSn = StrUtil.isNotBlank(live.getAircraftSn()) ? live.getAircraftSn() : live.getGatewaySn();
                    if (listResp != null && listResp.has("data") && listResp.get("data").has("list")) {
                        for (JsonNode c : listResp.get("data").get("list")) {
                            String cSn = c.has("sn") ? c.get("sn").asText("") : "";
                            String cCam = c.has("camera") ? c.get("camera").asText("") : "";
                            if (devSn.equals(cSn) && cam.equals(cCam)) {
                                converterId = c.has("converter_id") ? c.get("converter_id").asText("") : "";
                                if (StrUtil.isNotBlank(converterId)) break;
                            }
                        }
                    }
                } catch (Exception ignore) {}
            }
            boolean stopped = false;
            if (StrUtil.isNotBlank(converterId)) {
                try {
                    JsonNode resp = openApiClient.stopStreamConverter(projectUuid, converterId);
                    if (resp != null && resp.has("code") && resp.get("code").asInt() == 0) {
                        stopped = true;
                        System.out.println("[停码流转发器✅] 飞机=" + aircraftSn + " 司空2已关闭码流转发器(auto_push_stream=false)");
                    }
                } catch (Exception ignore) {}
            }
            if (!stopped) {
                stopByMqtt(live);
                System.out.println("[停码流转发器✅] 飞机=" + aircraftSn + " 已通过MQTT停止推流");
            }
            return true;
        } catch (Exception e) {
            System.out.println("[停码流转发器❌] 飞机=" + aircraftSn + " 失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void markLiveStreamStopped(String aircraftSn, String reason) {
        if (StrUtil.isBlank(aircraftSn)) return;
        try {
            LiveStream live = liveStreamMapper.selectOne(new LambdaQueryWrapper<LiveStream>()
                    .eq(LiveStream::getAircraftSn, aircraftSn)
                    .eq(LiveStream::getDeleted, 0)
                    .in(LiveStream::getStatus, 0, 1)
                    .orderByDesc(LiveStream::getCreateTime)
                    .last(" LIMIT 1"));
            if (live == null) return;
            LambdaUpdateWrapper<LiveStream> up = new LambdaUpdateWrapper<>();
            up.eq(LiveStream::getId, live.getId());
            up.set(LiveStream::getStatus, 2);
            up.set(LiveStream::getStopTime, LocalDateTime.now());
            up.set(LiveStream::getStatusMessage, reason);
            liveStreamMapper.update(null, up);
            System.out.println("[直播-status=2✅] 飞机=" + aircraftSn + " liveId=" + live.getId() + " reason=" + reason);
        } catch (Exception e) {
            System.out.println("[直播-status=2❌] 飞机=" + aircraftSn + " 更新失败: " + e.getMessage());
        }
    }

    @Override
    public LiveStream stopByAircraftSn(String aircraftSn) {
        if (StrUtil.isBlank(aircraftSn)) return null;

        // ========== 项目级隔离: 只停止默认项目下的飞机直播 ==========
        String defaultProjectUuid = openApiProperties.getDefaultProjectUuid();
        if (StrUtil.isNotBlank(defaultProjectUuid)) {
            try {
                LambdaQueryWrapper<com.dji.sikong.entity.Device> qw = new LambdaQueryWrapper<>();
                qw.eq(com.dji.sikong.entity.Device::getSn, aircraftSn).last(" LIMIT 1");
                com.dji.sikong.entity.Device device = deviceMapper.selectOne(qw);
                if (device != null && !defaultProjectUuid.equals(device.getProjectUuid())) {
                    System.out.println("[项目过滤❌停播拒绝] 飞机=" + aircraftSn + " 所属projectUuid=" + device.getProjectUuid()
                            + " ≠ 默认项目=" + defaultProjectUuid + " → 拒绝停止直播(防止跨项目误关)");
                    return null;
                }
            } catch (Exception e) {
                System.out.println("[项目过滤🔍停播] 查询device表异常 sn=" + aircraftSn + " err=" + e.getMessage() + " → 继续停播流程");
            }
        }

        LiveStream live = liveStreamMapper.selectOne(new LambdaQueryWrapper<LiveStream>()
                .eq(LiveStream::getAircraftSn, aircraftSn)
                .eq(LiveStream::getDeleted, 0)
                .in(LiveStream::getStatus, 0, 1)
                .orderByDesc(LiveStream::getCreateTime)
                .last(" LIMIT 1"));
        if (live == null) {
            // 退化：按 gatewaySn 查
            live = liveStreamMapper.selectOne(new LambdaQueryWrapper<LiveStream>()
                    .eq(LiveStream::getGatewaySn, aircraftSn)
                    .eq(LiveStream::getDeleted, 0)
                    .in(LiveStream::getStatus, 0, 1)
                    .orderByDesc(LiveStream::getCreateTime)
                    .last(" LIMIT 1"));
        }
        if (live == null) return null;
        try {
            String projectUuid = openApiProperties.getDefaultProjectUuid();

            // 1) 确定converter_id:优先live.getStreamId(),为空则listStreamConverters按sn+camera查
            String converterId = live.getStreamId();
            if (StrUtil.isBlank(converterId)) {
                try {
                    JsonNode listResp = openApiClient.listStreamConverters(projectUuid);
                    String cam = StrUtil.isNotBlank(live.getCameraIndex()) ? live.getCameraIndex() : "0";
                    String devSn = StrUtil.isNotBlank(live.getAircraftSn()) ? live.getAircraftSn() : live.getGatewaySn();
                    if (listResp != null && listResp.has("data") && listResp.get("data").has("list")) {
                        for (JsonNode c : listResp.get("data").get("list")) {
                            String cSn = c.has("sn") ? c.get("sn").asText("") : "";
                            String cCam = c.has("camera") ? c.get("camera").asText("") : "";
                            if (devSn.equals(cSn) && cam.equals(cCam)) {
                                converterId = c.has("converter_id") ? c.get("converter_id").asText("") : "";
                                if (StrUtil.isNotBlank(converterId)) {
                                    live.setStreamId(converterId);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception ignore) {}
            }

            // 2) 优先司空2 OpenAPI停止(PUT auto_push_stream=false)
            boolean stopped = false;
            if (StrUtil.isNotBlank(converterId)) {
                try {
                    JsonNode resp = openApiClient.stopStreamConverter(projectUuid, converterId);
                    if (resp != null && resp.has("code") && resp.get("code").asInt() == 0) {
                        live.setStatusMessage("司空2已关闭码流转发器(auto_push_stream=false)");
                        stopped = true;
                    }
                } catch (Exception ignore) {}
            }
            if (!stopped) {
                stopByMqtt(live);
            }
        } catch (Exception e) {
            live.setStatusMessage("停止异常: " + e.getMessage());
        }
        live.setStatus(2);
        live.setStopTime(LocalDateTime.now());
        liveStreamMapper.updateById(live);

        // ================ 知眸AI: 关码流成功后 → 只有真正的飞机码流才关! 机场/测试数据不调 ================
        try {
            String asn = live.getAircraftSn();
            // 用deviceType判断是否为真正的飞机(deviceType=3=AIRCRAFT), 过滤测试数据
            boolean realAircraft = false;
            if (StrUtil.isNotBlank(asn) && !asn.startsWith("TEST-")) {
                try {
                    LambdaQueryWrapper<com.dji.sikong.entity.Device> qwDev = new LambdaQueryWrapper<>();
                    qwDev.eq(com.dji.sikong.entity.Device::getSn, asn).last(" LIMIT 1");
                    com.dji.sikong.entity.Device dev = deviceMapper.selectOne(qwDev);
                    realAircraft = (dev != null && dev.getDeviceType() != null
                            && dev.getDeviceType().intValue() == com.dji.sikong.common.DeviceTypeConstants.AIRCRAFT);
                } catch (Exception ignore) {}
            }
            if (!realAircraft) {
                System.out.println("[知眸-删除🚫关任务] liveId=" + live.getId() + " 不是真正飞机码流 asn=[" + asn + "] → 不调scheduleTaskClose(删除非飞机逻辑)");
            } else {
                zhimouTaskService.scheduleTaskCloseAfterLiveStop(live);
            }
        } catch (Exception e) {
            log.warn("[知眸-关任务] 入3秒延迟队列异常(不影响停播本身): " + e.getMessage(), e);
        }

        return live;
    }

    private void stopByMqtt(LiveStream live) {
        if (mqttClient == null) return;
        if (StrUtil.isBlank(live.getGatewaySn()) && StrUtil.isBlank(live.getAircraftSn())) return;
        try {
            String tid = UUID.randomUUID().toString().replace("-", "");
            String bid = UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("tid", tid);
            msgBody.put("bid", bid);
            msgBody.put("timestamp", System.currentTimeMillis());
            msgBody.put("gateway", StrUtil.isNotBlank(live.getGatewaySn()) ? live.getGatewaySn() : live.getAircraftSn());
            msgBody.put("method", "live_stop_push");
            Map<String, Object> data = new HashMap<>();
            String sn = StrUtil.isNotBlank(live.getAircraftSn()) ? live.getAircraftSn() : live.getGatewaySn();
            data.put("sn", sn);
            if (StrUtil.isNotBlank(live.getCameraIndex())) data.put("camera_index", live.getCameraIndex());
            msgBody.put("data", data);
            String msg = JSONUtil.toJsonStr(msgBody);

            // 双topic下发:机场SN + 飞机SN,确保停推消息被收到
            java.util.HashSet<String> topics = new java.util.HashSet<>();
            if (StrUtil.isNotBlank(live.getGatewaySn())) topics.add("thing/product/" + live.getGatewaySn() + "/services");
            if (StrUtil.isNotBlank(live.getAircraftSn())) topics.add("thing/product/" + live.getAircraftSn() + "/services");
            for (String topic : topics) {
                boolean ok = mqttClient.publish(topic, msg, 1);
                System.out.println("[停推MQTT下发 " + (ok ? "✅成功" : "❌失败")
                        + " topic=" + topic
                        + " method=live_stop_push"
                        + " sn=" + live.getAircraftSn() + "/" + live.getGatewaySn());
            }
        } catch (Exception e) {
            log.warn("停推MQTT下发失败: " + e.getMessage());
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30000L)
    public void syncStreamConverterStatusFromDji() {
        try {
            String projectUuid = openApiProperties.getDefaultProjectUuid();
            com.fasterxml.jackson.databind.JsonNode resp = openApiClient.listStreamConverters(projectUuid);
            if (resp == null || !resp.has("data")) return;
            com.fasterxml.jackson.databind.JsonNode data = resp.get("data");
            if (!data.has("list")) return;
            com.fasterxml.jackson.databind.JsonNode list = data.get("list");
            if (!list.isArray()) return;

            int synced = 0;
            for (com.fasterxml.jackson.databind.JsonNode item : list) {
                String state = item.has("state") ? item.get("state").asText() : "";
                String sn = item.has("sn") ? item.get("sn").asText() : "";
                String converterId = item.has("converter_id") ? item.get("converter_id").asText() : "";
                if (StrUtil.isBlank(sn) || StrUtil.isBlank(converterId)) continue;

                // 根据 converter_id 或 sn 查找本地 live_stream
                LambdaQueryWrapper<LiveStream> qw = new LambdaQueryWrapper<>();
                qw.eq(LiveStream::getDeleted, 0)
                        .and(w -> w.eq(LiveStream::getStreamId, converterId).or().eq(LiveStream::getAircraftSn, sn))
                        .orderByDesc(LiveStream::getId)
                        .last("LIMIT 1");
                LiveStream live = liveStreamMapper.selectOne(qw);
                if (live == null) continue;

                boolean needUpdate = false;
                // 只有当前状态不是已停止(2)时, 才允许同步为运行中(1)
                // 已停止的码流(飞机已降落)不应该被同步任务改回运行中
                if ("running".equals(state) && (live.getStatus() == null || (live.getStatus().intValue() != 1 && live.getStatus().intValue() != 2))) {
                    live.setStatus(1);
                    live.setStatusMessage("司空2同步: stream-converter state=running");
                    live.setStartTime(LocalDateTime.now());
                    needUpdate = true;
                } else if (!"running".equals(state) && live.getStatus() != null
                        && (live.getStatus().intValue() == 0 || live.getStatus().intValue() == 1)) {
                    // 司空2已停止但本地还显示开启中/运行中 → 不主动改2, 留给 stopByAircraftSn 处理, 只打日志
                    System.out.println("[码流状态同步⚠️] sn=" + sn + " converterId=" + converterId
                            + " 司空2 state=" + state + " 本地 status=" + live.getStatus() + " → 等待停推逻辑处理");
                }

                if (needUpdate) {
                    liveStreamMapper.updateById(live);
                    synced++;
                    System.out.println("[码流状态同步✅] sn=" + sn + " converterId=" + converterId
                            + " state=" + state + " → 本地 status 已更新为1(运行中)");
                }
            }
            if (synced > 0) {
                System.out.println("[码流状态同步汇总] 从司空2同步了 " + synced + " 条码流状态");
            }
        } catch (Exception e) {
            log.warn("[码流状态同步异常] err=" + e.getMessage());
        }
    }
}
