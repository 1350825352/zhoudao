package com.dji.sikong.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import com.dji.sikong.common.ImageExifUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.config.ZhimouProperties;
import com.dji.sikong.entity.Device;
import com.dji.sikong.entity.LiveStream;
import com.dji.sikong.entity.Wayline;
import com.dji.sikong.entity.ZhimouDetectionResult;
import com.dji.sikong.entity.ZhimouImageDetection;
import com.dji.sikong.entity.SelfModelImageDetection;
import com.dji.sikong.entity.WorkOrder;
import com.dji.sikong.entity.ZhimouTask;
import com.dji.sikong.entity.ZhimouTaskConfig;
import com.dji.sikong.mapper.DeviceMapper;
import com.dji.sikong.mapper.LiveStreamMapper;
import com.dji.sikong.mapper.WaylineMapper;
import com.dji.sikong.mapper.WorkOrderMapper;
import com.dji.sikong.mapper.ZhimouDetectionResultMapper;
import com.dji.sikong.mapper.ZhimouTaskMapper;
import com.dji.sikong.mqtt.DjiMqttClient;
import com.dji.sikong.openapi.ZhimouOpenApiClient;
import com.dji.sikong.service.ImageCleanupService;
import com.dji.sikong.service.ZhimouTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@Slf4j
@Service
public class ZhimouTaskServiceImpl extends ServiceImpl<ZhimouTaskMapper, ZhimouTask> implements ZhimouTaskService {

    @Resource
    private ZhimouOpenApiClient zhimouOpenApiClient;

    @Resource
    private ZhimouProperties properties;

    /** 二期照片识别专用落盘根目录 (与一期视频 zhimou-images 物理解耦) */
    @org.springframework.beans.factory.annotation.Value("${zhimou.image.detectRootDir:zhimou-image-detect}")
    private String detectRootDir;

    @Resource
    private ZhimouDetectionResultMapper detectionResultMapper;

    /** 二期新增: 知眸图片识别结果表 Mapper */
    @Resource
    private com.dji.sikong.mapper.ZhimouImageDetectionMapper zhimouImageDetectionMapper;

    /** 阶段3新增: 自有模型图片识别结果表 Mapper (用于 isBothSideCleaned DB 兜底检查) */
    @Resource
    private com.dji.sikong.mapper.SelfModelImageDetectionMapper selfModelImageDetectionMapper;

    @Resource
    private LiveStreamMapper liveStreamMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private DjiMqttClient djiMqttClient;

    @Resource
    private com.dji.sikong.mapper.DeviceMapper deviceMapper;

    @Resource
    private com.dji.sikong.config.DjiOpenApiProperties openApiProperties;

    @Resource
    private ImageCleanupService imageCleanupService;

    @Resource
    private com.dji.sikong.service.MediaServerStreamProbeService streamProbeService;

    @Resource
    private com.dji.sikong.config.DjiMediaProperties mediaProperties;

    @Resource
    private com.dji.sikong.mapper.FlightTaskMapper flightTaskMapper;

    @Resource
    private com.dji.sikong.mapper.WaylineMapper waylineMapper;

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Resource
    private com.dji.sikong.service.DeviceRealtimeStatusService realtimeStatusService;

    @Resource
    private com.dji.sikong.openapi.DjiOpenApiClient djiOpenApiClient;

    @Resource
    private com.dji.sikong.service.ZhimouTaskConfigService zhimouTaskConfigService;

    @org.springframework.context.annotation.Lazy
    @Resource
    private com.dji.sikong.service.LiveStreamService liveStreamService;

    /** 二期新增: 数据源开关服务 */
    @Resource
    private com.dji.sikong.service.TaskSwitchService taskSwitchService;

    private static final String LAST_FLIGHT_REPORT_TS_KEY = "zhimou:last_flight_report_ts:";
    private static final String ZHIMOU_ALERT_TOPIC_PREFIX = "zhimou/alert/";

    // ================================================================
    // 基于HTTP-FLV流检测的1秒调度器
    // 核心逻辑:
    //   1. 只看SRS上有没有HTTP-FLV流（不检查数据库status）
    //   2. 每1秒检测一次 HTTP-FLV 视频流是否可播放
    //   3. 连续500ms可播放 且 zhimou_status!=1 → 创建知眸任务
    //   4. 连续2秒不可播放 → 关闭知眸任务
    //   5. 完全不判断飞机状态/直播status(只看SRS有没有流)
    // ================================================================

    /** 旧延迟队列: 开播/关播延迟触发任务创建/关闭(被2秒流检测调度器替代,但保留以兼容现有调用) */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Object[]> OPEN_TASK_DELAY_QUEUE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, Object[]> CLOSE_TASK_DELAY_QUEUE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 防抖状态: key=liveId, value=[连续可播放开始时间ms, 连续不可播放开始时间ms] */
    private static final java.util.concurrent.ConcurrentHashMap<Long, long[]> STREAM_DEBOUNCE_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    /** 知眸任务状态检查时间: key=liveId, value=上次检查时间ms(每3秒查一次) */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Long> ZHIMOU_TASK_CHECK_MAP = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CREATE_DEBOUNCE_MS = 500L;   // 连续500ms可播放才创建(加速)
    private static final long CLOSE_DEBOUNCE_MS = 2000L;   // 连续2秒不可播放才关闭(防抖动)
    private static final int MAX_CREATE_RETRY = 3;            // 创建失败最多重试3次

    /** 1秒调度器: 检测所有运行中直播的HTTP-FLV流状态, 自动创建/关闭知眸任务 */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 1000L)
    public void unifiedScheduler2Seconds() {
        // ⭐ 二期新增: 检查视频任务开关, 关闭时立即关闭所有运行中的知眸视频任务, 并停止创建新任务
        boolean videoTaskEnabled = false;
        try {
            videoTaskEnabled = taskSwitchService.isVideoTaskEnabled();
        } catch (Exception e) {
            // Redis/DB 不可用时, 默认开启(不影响一期逻辑)
        }
        if (!videoTaskEnabled) {
            // 开关关闭: 立即关闭所有运行中的知眸视频任务(方案B)
            try {
                LambdaQueryWrapper<LiveStream> closeQw = new LambdaQueryWrapper<>();
                closeQw.eq(LiveStream::getDeleted, 0).eq(LiveStream::getZhimouStatus, 1);
                List<LiveStream> runningStreams = liveStreamMapper.selectList(closeQw);
                for (LiveStream live : runningStreams) {
                    try {
                        closeZhimouTaskByLiveStreamId(live.getId());
                        System.out.println("[知眸-开关] 视频任务关闭, 关闭运行中任务 liveId=" + live.getId());
                    } catch (Exception ex) {
                        System.out.println("[知眸-开关] 关闭任务异常 liveId=" + live.getId() + " err=" + ex.getMessage());
                    }
                }
                // 清空防抖状态
                STREAM_DEBOUNCE_MAP.clear();
                ZHIMOU_TASK_CHECK_MAP.clear();
            } catch (Exception ex) {
                System.out.println("[知眸-开关] 关闭运行中视频任务异常: " + ex.getMessage());
            }
            return; // 不再创建新任务
        }
        System.out.println("[知眸-DEBUG] unifiedScheduler2Seconds 被调用了! ts=" + System.currentTimeMillis());
        final long now = System.currentTimeMillis();
        int detectCnt = 0, createCnt = 0, closeCnt = 0, skipCnt = 0;
        try {
            // 1. 查询最近24小时内有aircraft_sn的直播流（不检查status，只看SRS有没有流）
            LambdaQueryWrapper<LiveStream> qw = new LambdaQueryWrapper<>();
            qw.eq(LiveStream::getDeleted, 0)
              .isNotNull(LiveStream::getAircraftSn).ne(LiveStream::getAircraftSn, "");
            qw.apply("aircraft_sn NOT LIKE 'TEST-%'");
            qw.ge(LiveStream::getCreateTime, LocalDateTime.now().minusHours(24));
            qw.orderByDesc(LiveStream::getId);
            List<LiveStream> activeLives = liveStreamMapper.selectList(qw);

            if (CollectionUtils.isEmpty(activeLives)) {
                // 没有运行中的直播 → 清空所有防抖状态
                STREAM_DEBOUNCE_MAP.clear();
                ZHIMOU_TASK_CHECK_MAP.clear();
                return;
            }

            // ====== 关键优化: 同一飞机+摄像头只保留最新(id最大)的1条记录, 避免历史旧记录重复创建知眸任务浪费并发 ======
            //      例如: 同一飞机SN有13条历史记录, 但只会创建1个知眸任务, 不会触发50008并发不足
            java.util.Map<String, LiveStream> latestLiveByKey = new java.util.LinkedHashMap<>();
            for (LiveStream l : activeLives) {
                String key = StrUtil.blankToDefault(l.getAircraftSn(), "") + "|" + StrUtil.blankToDefault(l.getCameraIndex(), "");
                // 已按id倒序, 第一个遇到的就是最新的
                latestLiveByKey.computeIfAbsent(key, k -> l);
            }
            if (latestLiveByKey.size() < activeLives.size()) {
                System.out.println("[知眸-调度器-去重🗑️] 同一飞机+摄像头去重: 原始记录=" + activeLives.size() + " 条 → 去重后=" + latestLiveByKey.size() + " 条 (节省知眸并发)");
            }
            List<LiveStream> dedupedLives = new java.util.ArrayList<>(latestLiveByKey.values());

            // 清理已不存在的直播的防抖状态 (按去重后的活跃id)
            Set<Long> activeIds = new java.util.HashSet<>();
            for (LiveStream l : dedupedLives) activeIds.add(l.getId());
            STREAM_DEBOUNCE_MAP.keySet().removeIf(id -> !activeIds.contains(id));
            ZHIMOU_TASK_CHECK_MAP.keySet().removeIf(id -> !activeIds.contains(id));

            // 后续循环使用去重后的列表, 不是activeLives
            activeLives = dedupedLives;

            String ymlApp = (properties.getOpenapi() == null) ? null : properties.getOpenapi().getDefaultAppId();
            String ymlPkg = (properties.getOpenapi() == null) ? null : properties.getOpenapi().getDefaultPkgId();

            // 2. 逐个检测流状态
            for (LiveStream live : activeLives) {
                Long liveId = live.getId();
                detectCnt++;

                // 探测可播放的HTTP-FLV地址(先内网后外网),返回第一个可播放的地址
                String httpFlvUrl = null;
                boolean playable = false;
                try {
                    httpFlvUrl = streamProbeService.probePlayableHttpFlvUrl(live);
                    playable = StrUtil.isNotBlank(httpFlvUrl);
                    System.out.println("[知眸-流检测-结果🔍] liveId=" + liveId
                            + " 飞机=" + live.getAircraftSn()
                            + " 可播放地址=" + (playable ? httpFlvUrl : "无(内网+外网均不可播放)")
                            + " 可播放=" + playable
                            + " zhimou_status=" + live.getZhimouStatus());
                } catch (Exception ex) {
                    System.out.println("[知眸-流检测-异常⚠️] liveId=" + liveId + " err=" + ex.getMessage());
                }

                if (!playable) {
                    // 都不可播放 → 更新防抖状态后继续
                }

                // 防抖处理
                long[] debounce = STREAM_DEBOUNCE_MAP.computeIfAbsent(liveId, k -> new long[]{0L, 0L});
                if (playable) {
                    // 流可播放 → 更新连续可播放开始时间, 清连续不可播放时间
                    if (debounce[0] == 0) debounce[0] = now;
                    debounce[1] = 0L;
                } else {
                    // 流不可播放 → 更新连续不可播放开始时间, 清连续可播放时间
                    if (debounce[1] == 0) debounce[1] = now;
                    debounce[0] = 0L;
                }

                Integer zs = live.getZhimouStatus();
                boolean zhimouRunning = (zs != null && zs.intValue() == 1);

                // ---- 定期检查知眸任务真实状态(每3秒查一次,防止任务假死) ----
                // ⚠️私有化服务器不支持 GET /api/task/{taskId},改用 GET /api/task 列表接口判断任务是否存在
                if (zhimouRunning && StrUtil.isNotBlank(live.getZhimouTaskId())) {
                    Long lastCheck = ZHIMOU_TASK_CHECK_MAP.get(liveId);
                    if (lastCheck == null || (now - lastCheck) > 3000L) {
                        ZHIMOU_TASK_CHECK_MAP.put(liveId, now);
                        try {
                            String taskId = live.getZhimouTaskId();
                            JsonNode taskList = zhimouOpenApiClient.listTasks();
                            if (taskList == null) {
                                // 查询失败(网络/超时/HTTP错误) → 不处理,下次再查
                                System.out.println("[知眸-任务状态检查⚠️] liveId=" + liveId + " taskId=" + taskId + " 任务列表查询失败,跳过本次检查");
                            } else if (taskList.isNull()) {
                                // data:null → 知眸服务器上无任何任务 → 当前任务已不存在
                                System.out.println("[知眸-任务状态检查🔴] liveId=" + liveId + " taskId=" + taskId + " 知眸任务列表为空(无任何任务) → 重置zhimou_status=2,等待重新创建");
                                updateLiveStreamZhimouStatus(liveId, 2, null, "知眸任务列表为空(任务已结束或被清理)", null, null);
                                zhimouRunning = false;
                                zs = 2;  // 同步更新本地变量,允许本循环内重新创建
                            } else {
                                boolean taskExists = false;
                                String taskCloseCode = "";
                                if (taskList.isArray()) {
                                    for (JsonNode task : taskList) {
                                        if (taskId.equals(task.path("task_id").asText(""))) {
                                            taskExists = true;
                                            taskCloseCode = task.path("close_code").asText("");
                                            break;
                                        }
                                    }
                                }
                                System.out.println("[知眸-任务状态检查🔍] liveId=" + liveId + " taskId=" + taskId
                                        + " 任务存在=" + taskExists + " close_code=" + taskCloseCode);
                                if (!taskExists) {
                                    // 任务不在列表中 → 已结束/被清理 → 重置zhimou_status=2,让调度器重新创建
                                    System.out.println("[知眸-任务状态检查🔴] liveId=" + liveId + " 知眸任务不在列表中(已结束) → 重置zhimou_status=2,等待重新创建");
                                    updateLiveStreamZhimouStatus(liveId, 2, null, "知眸任务不在列表中(已结束或被清理)", null, null);
                                    zhimouRunning = false;
                                    zs = 2;  // 同步更新本地变量,允许本循环内重新创建
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("[知眸-任务状态检查-异常⚠️] liveId=" + liveId + " err=" + ex.getMessage());
                        }
                    }
                }

                // ---- 判断是否需要: 关知眸任务(连续2秒不可播放) ----
                // ⚠️注意: 知眸任务只与SRS流媒体服务器相关,与码流转发器无关
                // 这里只关知眸任务,不碰 status(那是码流转发器的参数,由handleModeCode控制)
                if (!playable) {
                    long notPlayableMs = (debounce[1] > 0) ? (now - debounce[1]) : 0L;
                    if (notPlayableMs >= CLOSE_DEBOUNCE_MS) {
                        // 连续2秒SRS流不可播放 → 只关知眸任务(不碰status,码流转发器可能在切镜头)
                        System.out.println("[流检测-关知眸🔴] liveId=" + liveId
                                + " 飞机=" + live.getAircraftSn()
                                + " 连续不可播放=" + (notPlayableMs/1000.0) + "s ≥ " + (CLOSE_DEBOUNCE_MS/1000) + "s → 只关知眸任务(不碰status)");

                        // 只关知眸任务(如果在运行) — status由码流转发器控制,这里不动
                        if (zhimouRunning) {
                            boolean zsOk = closeZhimouTaskByLiveStreamId(liveId);
                            if (zsOk) closeCnt++;
                            System.out.println("[流检测-关知眸✅] liveId=" + liveId + " 飞机=" + live.getAircraftSn()
                                    + " 已关闭知眸任务(zhimou_status=2) → SRS流恢复后可重新创建");
                            // 关闭后立即更新本地变量,允许后续在同一循环内重新创建(如果流恢复)
                            zhimouRunning = false;
                            zs = 2;
                        }

                        // 清防抖状态(无论是否成功,避免反复触发关闭)
                        debounce[0] = 0L;
                        debounce[1] = 0L;
                    } else {
                        skipCnt++;
                        if (notPlayableMs > 0 && notPlayableMs % 2000 < 2000) {
                            System.out.println("[流检测-关流防抖⏳] liveId=" + liveId
                                    + " 流不可播放但未到阈值: " + (notPlayableMs/1000.0) + "s < " + (CLOSE_DEBOUNCE_MS/1000) + "s → 继续观察(给网络恢复留时间)");
                        }
                    }
                }

                // ---- 判断是否需要重新创建知眸任务(流恢复后) ----
                // 注意: 这里不依赖zhimouRunning变量,而是直接查数据库获取最新状态
                // 因为上面可能刚关闭了知眸任务,zhimouRunning已更新为false
                if (playable) {
                    // 流可播放 → 重新查询数据库获取最新zhimou_status(可能刚被关闭=2)
                    Integer latestZs = live.getZhimouStatus();
                    // 如果本循环内刚关闭过,zs已被更新为2,直接用zs判断
                    if (zs != null && zs == 2) latestZs = 2;

                    if (latestZs != null && latestZs != 1) {
                        // zhimou_status≠1(未运行) → 可以创建
                        long playableMs = (debounce[0] > 0) ? (now - debounce[0]) : 0L;
                        if (playableMs >= CREATE_DEBOUNCE_MS) {
                            System.out.println("[知眸-流检测-创建任务🟢] liveId=" + liveId
                                    + " 飞机=" + live.getAircraftSn()
                                    + " 连续可播放=" + (playableMs/1000.0) + "s ≥ " + (CREATE_DEBOUNCE_MS/1000) + "s → 创建知眸任务"
                                    + " 当前zhimou_status=" + latestZs + "(可创建)");
                            String appId = StrUtil.isNotBlank(ymlApp) ? ymlApp : live.getZhimouAppId();
                            String pkgId = StrUtil.isNotBlank(ymlPkg) ? ymlPkg : live.getZhimouPkgId();
                            String taskId = createVideoTaskWhenLiveStart(live, appId, pkgId, httpFlvUrl);
                            if (StrUtil.isNotBlank(taskId)) {
                                createCnt++;
                                debounce[0] = 0L;
                                debounce[1] = 0L;
                            }
                        } else {
                            skipCnt++;
                            if (playableMs > 0 && playableMs % 2000 < 2000) {
                                System.out.println("[知眸-流检测-创建防抖⏳] liveId=" + liveId
                                        + " 流可播放但未到防抖阈值: " + (playableMs/1000.0) + "s < " + (CREATE_DEBOUNCE_MS/1000) + "s → 继续观察");
                            }
                        }
                    }
                }
            }

            // 汇总日志(有动作时才打印)
            if (createCnt > 0 || closeCnt > 0) {
                System.out.println("[知眸-流检测-汇总📊] 检测活跃直播=" + detectCnt
                        + " 创建任务=" + createCnt + " 关闭任务=" + closeCnt + " 防抖等待中=" + skipCnt);
            }

        } catch (Exception e) {
            log.warn("[知眸-流检测-调度器⚠️] 异常: {}", e.getMessage(), e);
            System.out.println("[知眸-流检测-调度器-异常⚠️] err=" + e.getMessage());
        } finally {
            // 心跳日志
            String beat = "[知眸-2s调度心跳💓] ts=" + java.time.LocalTime.now()
                    + " 检测活跃直播=" + detectCnt + " 防抖Map大小=" + STREAM_DEBOUNCE_MAP.size();
            System.out.println(beat);
            log.info(beat);
        }
    }

    @Override
    public void scheduleTaskOpenAfterLiveStart(LiveStream liveStream, String appId, String applicationPackageId) {
        if (liveStream == null || liveStream.getId() == null) return;
        Long liveId = liveStream.getId();
        String asn = liveStream.getAircraftSn();
        OPEN_TASK_DELAY_QUEUE.put(liveId, new Object[]{System.currentTimeMillis(), appId, applicationPackageId, 0});
        CLOSE_TASK_DELAY_QUEUE.remove(liveId);
        System.out.println("[知眸-调度-入队🟢] 开码流成功! liveId=" + liveId + " 飞机=" + asn + " → 已入『5秒后创建知眸』队列, appIds=[" + appId + "], pkgId=[" + applicationPackageId + "]");
    }

    @Override
    public void scheduleTaskCloseAfterLiveStop(LiveStream liveStream) {
        if (liveStream == null || liveStream.getId() == null) return;
        Long liveId = liveStream.getId();
        String asn = liveStream.getAircraftSn();
        CLOSE_TASK_DELAY_QUEUE.put(liveId, new Object[]{System.currentTimeMillis(), asn});
        OPEN_TASK_DELAY_QUEUE.remove(liveId);
        System.out.println("[知眸-调度-入队🔴] 关码流成功! liveId=" + liveId + " 飞机=" + asn + " → 已入『3秒后关闭知眸』队列");
    }

    // ================================================================
    // 应用(包)查询
    // ================================================================
    @Override
    public JsonNode listApplicationPackages() {
        return zhimouOpenApiClient.listApplicationPackages();
    }

    @Override
    public JsonNode listAppsByPackage(String applicationPackageId) {
        return zhimouOpenApiClient.listAppsByPackage(applicationPackageId);
    }

    @Override
    public String getDefaultAppId() {
        try {
            return properties.getOpenapi() != null ? properties.getOpenapi().getDefaultAppId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getDefaultPkgId() {
        try {
            return properties.getOpenapi() != null ? properties.getOpenapi().getDefaultPkgId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public JsonNode listTasks() {
        return zhimouOpenApiClient.listTasks();
    }

    @Override
    public JsonNode deleteTask(String taskId) {
        return zhimouOpenApiClient.deleteTask(taskId);
    }

    // ================================================================
    // 创建/关闭知眸任务
    // ================================================================

    /** 兼容旧接口: 自动从liveStream构建HTTP-FLV地址 */
    @Override
    public String createVideoTaskWhenLiveStart(LiveStream liveStream, String appId, String applicationPackageId) {
        String httpFlvUrl = (liveStream != null) ? streamProbeService.buildHttpFlvPullUrl(liveStream) : null;
        return createVideoTaskWhenLiveStart(liveStream, appId, applicationPackageId, httpFlvUrl);
    }

    /**
     * 创建知眸任务(新接口, 支持传入HTTP-FLV地址)
     * @param httpFlvUrl 传给知眸的视频拉流地址(HTTP-FLV, 与screen-v3前端用的同一个)
     */
    public synchronized String createVideoTaskWhenLiveStart(LiveStream liveStream, String appId, String applicationPackageId, String httpFlvUrl) {
        if (liveStream == null || liveStream.getId() == null) {
            log.error("[知眸-创建任务] liveStream为空");
            return null;
        }
        Long liveId = liveStream.getId();
        // 1. 重查DB拿最新live_stream对象, 保证status/url是最新的(参数可能是旧对象)
        LiveStream latestLive = liveStreamMapper.selectById(liveId);
        if (latestLive == null) {
            System.out.println("[知眸-创建任务-跳过⚠️] DB无liveId=" + liveId + " 的记录,直接返回");
            return null;
        }

        // ====== 不依赖数据库zhimou_status, 直接按序列号查知眸端是否有运行中任务 ======
        String deviceSn = StrUtil.isNotBlank(latestLive.getAircraftSn()) ? latestLive.getAircraftSn() : latestLive.getGatewaySn();

        if (StrUtil.isNotBlank(deviceSn)) {
            // 查该序列号最新的有真实task_id(非tmp_)且未关闭的记录
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ZhimouTask> snQw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            snQw.eq(ZhimouTask::getAircraftSn, deviceSn)
                .notLike(ZhimouTask::getZhimouTaskId, "tmp_%")
                .isNotNull(ZhimouTask::getZhimouTaskId)
                .ne(ZhimouTask::getZhimouTaskId, "")
                .in(ZhimouTask::getStatus, 0, 1)
                .orderByDesc(ZhimouTask::getId)
                .last("LIMIT 1");
            ZhimouTask snExistTask = this.getOne(snQw, false);
            if (snExistTask != null && StrUtil.isNotBlank(snExistTask.getZhimouTaskId())) {
                String existingTaskId = snExistTask.getZhimouTaskId();
                System.out.println("[知眸-创建任务-序列号查重🔍] liveId=" + liveId
                        + " 飞机=" + deviceSn + " 数据库有旧任务 task_id=" + existingTaskId
                        + " → 检查知眸服务端是否真的存在...");

                // 额外检查: 该task_id在知眸服务端是否真的存在
                JsonNode taskList = zhimouOpenApiClient.listTasks();
                boolean taskAlive = false;
                if (taskList != null && taskList.isArray()) {
                    for (JsonNode task : taskList) {
                        JsonNode tid = task.get("task_id");
                        if (tid != null && existingTaskId.equals(tid.asText())) {
                            taskAlive = true;
                            break;
                        }
                    }
                } else if (taskList != null && taskList.isNull()) {
                    // data:null → 知眸服务端无任何任务
                    taskAlive = false;
                } else {
                    // 查询结果异常(null/非数组/非null) → 保守复用,不阻断创建
                    System.out.println("[知眸-创建任务-序列号查重⚠️] listTasks返回异常: " + (taskList == null ? "null" : taskList.toString()) + " → 保守复用旧任务");
                    taskAlive = true;
                }

                if (taskAlive) {
                    System.out.println("[知眸-创建任务-序列号查重✅] liveId=" + liveId
                            + " 飞机=" + deviceSn + " 知眸服务端确认任务存在 task_id=" + existingTaskId
                            + " → 复用,不重复创建");
                    updateLiveStreamZhimouStatus(liveId, 1, existingTaskId, "复用已有任务", null, null);
                    return existingTaskId;
                } else {
                    System.out.println("[知眸-创建任务-序列号查重🔴] liveId=" + liveId
                            + " 飞机=" + deviceSn + " 知眸服务端任务已不存在(服务重启/被清理)"
                            + " → 清理旧记录,创建新任务");
                    // 清理旧记录
                    snExistTask.setStatus(2);
                    snExistTask.setStopTime(java.time.LocalDateTime.now());
                    snExistTask.setStatusMessage("知眸服务端任务已不存在(查重清理)");
                    this.updateById(snExistTask);
                    updateLiveStreamZhimouStatus(liveId, 2, null, "知眸服务端任务已不存在(查重清理)", null, null);
                    // 继续往下创建新任务
                }
            }
        }
        // 4. 解析appId(支持逗号分隔多个)
        String rawApp = StrUtil.isNotBlank(appId) ? appId : "";
        // 5. 拿inputUrl: 优先使用传入的HTTP-FLV地址(与screen-v3前端同一个), 没有就从liveStream构建
        String inputUrl = StrUtil.isNotBlank(httpFlvUrl) ? httpFlvUrl : streamProbeService.buildHttpFlvPullUrl(latestLive);
        if (StrUtil.isBlank(inputUrl)) {
            // 兜底: 用旧逻辑从pushUrl/pullUrl拿
            inputUrl = StrUtil.isNotBlank(latestLive.getPushUrl()) ? latestLive.getPushUrl() : latestLive.getPullUrl();
        }
        System.out.println("[知眸-创建任务-video_url🔗] liveId=" + liveId + " 飞机=" + deviceSn
                + " video_url=" + inputUrl + " (与screen-v3前端实时直播使用同一个HTTP-FLV地址)");

        // 5. 组装知眸createTask参数(优先从数据库配置读取,降级使用硬编码)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "video");
        body.put("video_url", inputUrl);
        body.put("device_id", deviceSn);
        body.put("business_id", String.valueOf(liveId));

        java.util.List<String> appIds = new java.util.ArrayList<>();
        String callback = null;

        ZhimouTaskConfig config = zhimouTaskConfigService.getConfig();
        if (config != null) {
            // 从数据库配置读取
            if (StrUtil.isNotBlank(config.getAppIds())) {
                try {
                    cn.hutool.json.JSONArray appIdArray = JSONUtil.parseArray(config.getAppIds());
                    for (int i = 0; i < appIdArray.size(); i++) {
                        String aid = appIdArray.getStr(i);
                        if (StrUtil.isNotBlank(aid)) appIds.add(aid.trim());
                    }
                } catch (Exception e) {
                    System.out.println("[知眸-创建任务] 解析appIds JSON异常: " + e.getMessage());
                }
            }
            body.put("app_ids", appIds);

            if (StrUtil.isNotBlank(config.getZapkId())) {
                body.put("zapk_id", config.getZapkId());
            }

            callback = config.getReportUrl();
            if (StrUtil.isBlank(callback)) {
                callback = properties.getOpenapi().getCallbackUrl();
            }
            if (StrUtil.isNotBlank(callback)) {
                body.put("report_url", callback);
            }

            String outputUrl = config.getOutputUrl();
            if (StrUtil.isBlank(outputUrl)) {
                outputUrl = properties.getOpenapi().getOutputUrlTemplate();
            }
            if (StrUtil.isNotBlank(outputUrl)) {
                body.put("output_url", outputUrl.replace("{task_id}", "tmp_" + liveId));
            }

            Map<String, Object> osd = new LinkedHashMap<>();
            osd.put("display_name", config.getOsdDisplayName() != null && config.getOsdDisplayName() == 1);
            osd.put("display_box", config.getOsdDisplayBox() != null && config.getOsdDisplayBox() == 1);
            osd.put("display_track", config.getOsdDisplayTrack() != null && config.getOsdDisplayTrack() == 1);
            osd.put("font_size", config.getOsdFontSize() != null ? config.getOsdFontSize() : 0);
            osd.put("border_size", config.getOsdBorderSize() != null ? config.getOsdBorderSize() : 0);
            body.put("osd", osd);
        } else {
            // 降级使用硬编码默认值
            if (StrUtil.isNotBlank(rawApp)) {
                String[] appIdArray = rawApp.split(",");
                for (String aid : appIdArray) {
                    String trimmed = aid.trim();
                    if (StrUtil.isNotBlank(trimmed)) appIds.add(trimmed);
                }
            }
            body.put("app_ids", appIds);
            if (StrUtil.isNotBlank(applicationPackageId)) {
                body.put("zapk_id", applicationPackageId);
            }
            callback = properties.getOpenapi().getCallbackUrl();
            if (StrUtil.isNotBlank(callback)) body.put("report_url", callback);
            String outputTpl = properties.getOpenapi().getOutputUrlTemplate();
            if (StrUtil.isNotBlank(outputTpl)) {
                body.put("output_url", outputTpl.replace("{task_id}", "tmp_" + liveId));
            }
            Map<String, Object> osd = new LinkedHashMap<>();
            osd.put("display_name", true);
            osd.put("display_box", true);
            osd.put("display_track", true);
            osd.put("font_size", 0);
            osd.put("border_size", 0);
            body.put("osd", osd);
        }

        String reqJson = JSONUtil.toJsonStr(body);
        log.info("[知眸-创建任务] 请求参数: {}", reqJson);
        System.out.println("[知眸-创建任务-请求📤] liveId=" + liveId + " 飞机=" + deviceSn
                + " inputUrl=" + inputUrl + " appIds=[" + rawApp + "] pkgId=" + applicationPackageId
                + " app_ids.size=" + appIds.size() + " report_url=" + callback);

        // 6. 查询航线UUID(一次飞行任务期间固定不变,无论任务成功失败都要查询)
        String waylineUuid = queryWaylineUuid(deviceSn);
        if (StrUtil.isBlank(waylineUuid)) {
            waylineUuid = "手动飞行";
        }
        log.info("[知眸-创建任务] 查询到航线UUID: aircraftSn={}, waylineUuid={}", deviceSn, waylineUuid);

        // 7. 直接调知眸API(不做任何拦截,就算参数为空也调,让知眸API告诉我们真实错误)
        JsonNode resp = zhimouOpenApiClient.createTask(body);
        if (resp == null) {
            String realErr = zhimouOpenApiClient.getLastErrorDetail();
            String errMsg = StrUtil.isNotBlank(realErr) ? realErr : "知眸createTask返回空(HTTP失败或网络超时)";
            System.out.println("[知眸-创建任务-结果❌失败] liveId=" + liveId + " 原因: " + errMsg);
            log.error("[知眸-创建任务] 失败 aircraftSn={}, 真实错误={}", deviceSn, errMsg);
            updateLiveStreamZhimouStatus(liveId, 3, null, errMsg, rawApp, applicationPackageId);
            saveZhimouTask(latestLive, null, rawApp, applicationPackageId, inputUrl, 3, reqJson, null, errMsg, waylineUuid);
            return null;
        }

        String zhimouTaskId = resp.path("task_id").asText(null);
        if (StrUtil.isBlank(zhimouTaskId)) zhimouTaskId = resp.path("id").asText(null);
        if (StrUtil.isBlank(zhimouTaskId)) {
            String detail = resp.toString();
            String shortDetail = detail.length() > 200 ? detail.substring(0, 200) : detail;
            System.out.println("[知眸-创建任务-结果❌失败] liveId=" + liveId + " 返回体无task_id字段 | resp=" + shortDetail);
            updateLiveStreamZhimouStatus(liveId, 3, null, "返回体无task_id: " + shortDetail, rawApp, applicationPackageId);
            saveZhimouTask(latestLive, null, rawApp, applicationPackageId, inputUrl, 3, reqJson, JSONUtil.toJsonStr(resp), "返回体无task_id字段", waylineUuid);
            return null;
        }

        // 8. 成功!回填 live_stream + zhimou_task
        System.out.println("[知眸-创建任务-结果🟢成功] liveId=" + liveId + " 飞机=" + deviceSn + " zhimouTaskId=" + zhimouTaskId + " waylineUuid=" + waylineUuid);
        log.info("[知眸-创建任务] 成功 task_id={}, aircraftSn={}, waylineUuid={}", zhimouTaskId, deviceSn, waylineUuid);
        updateLiveStreamZhimouStatus(liveId, 1, zhimouTaskId, null, rawApp, applicationPackageId);
        saveZhimouTask(latestLive, zhimouTaskId, rawApp, applicationPackageId, inputUrl, 1, reqJson, JSONUtil.toJsonStr(resp), null, waylineUuid);

        // 8. 自动调用ailive接口获取AI视频并推流到配置的RTMP地址
        try {
            String rtmpUrlAi = (mediaProperties.getStreamServer() == null) ? null : mediaProperties.getStreamServer().getRtmpUrlAi();
            if (StrUtil.isNotBlank(rtmpUrlAi)) {
                String streamKey = "";
                if (StrUtil.isNotBlank(latestLive.getAircraftSn())) {
                    streamKey = latestLive.getAircraftSn();
                    if (StrUtil.isNotBlank(latestLive.getCameraIndex())) {
                        streamKey += "/" + latestLive.getCameraIndex();
                    }
                } else if (StrUtil.isNotBlank(latestLive.getGatewaySn())) {
                    streamKey = latestLive.getGatewaySn();
                }

                String secret = "";
                String originPushUrl = latestLive.getPushUrl();
                if (StrUtil.isNotBlank(originPushUrl)) {
                    int qIndex = originPushUrl.indexOf("?secret=");
                    if (qIndex >= 0) {
                        secret = originPushUrl.substring(qIndex + 8);
                        int nextParam = secret.indexOf("&");
                        if (nextParam > 0) secret = secret.substring(0, nextParam);
                    }
                }

                String aiRtmpUrl = rtmpUrlAi.replace("{streamKey}", streamKey).replace("{secret}", secret);

                java.util.List<String> appIdList = new java.util.ArrayList<>();
                if (StrUtil.isNotBlank(rawApp)) {
                    String[] appIdArray = rawApp.split(",");
                    for (String aid : appIdArray) {
                        String trimmed = aid.trim();
                        if (StrUtil.isNotBlank(trimmed)) appIdList.add(trimmed);
                    }
                }

                System.out.println("[知眸-AI推流🟢] 开始调用ailive接口 liveId=" + liveId + " taskId=" + zhimouTaskId + " rtmp=" + aiRtmpUrl + " appIds=" + appIdList);
                JsonNode aiResp = zhimouOpenApiClient.getAiVideoLive(zhimouTaskId, aiRtmpUrl, null, appIdList);
                if (aiResp != null) {
                    System.out.println("[知眸-AI推流-结果✅] liveId=" + liveId + " taskId=" + zhimouTaskId + " resp=" + aiResp.toString());
                    String aiStreamUrl = aiResp.path("data").path("stream").asText("");
                    if (StrUtil.isNotBlank(aiStreamUrl)) {
                        System.out.println("[知眸-AI推流] AI视频流地址: " + aiStreamUrl);
                    }
                } else {
                    System.out.println("[知眸-AI推流-结果❌] liveId=" + liveId + " taskId=" + zhimouTaskId + " 调用ailive接口返回空");
                }
            } else {
                System.out.println("[知眸-AI推流-跳过] 未配置rtmp-url-ai, 跳过AI视频推流");
            }
        } catch (Exception e) {
            System.out.println("[知眸-AI推流-异常❌] liveId=" + liveId + " taskId=" + zhimouTaskId + " err=" + e.getMessage());
            log.warn("[知眸-AI推流异常]", e);
        }

        return zhimouTaskId;
    }

    private void updateLiveStreamZhimouStatus(Long liveStreamId, int zhimouStatus, String zhimouTaskId,
                                              String lastError, String appId, String pkgId) {
        try {
            LambdaUpdateWrapper<LiveStream> up = new LambdaUpdateWrapper<>();
            up.eq(LiveStream::getId, liveStreamId);
            up.set(LiveStream::getZhimouStatus, zhimouStatus);
            if (StrUtil.isNotBlank(zhimouTaskId)) up.set(LiveStream::getZhimouTaskId, zhimouTaskId);
            if (StrUtil.isNotBlank(lastError))   up.set(LiveStream::getZhimouLastError, lastError);
            if (StrUtil.isNotBlank(appId))       up.set(LiveStream::getZhimouAppId, appId);
            if (StrUtil.isNotBlank(pkgId))       up.set(LiveStream::getZhimouPkgId, pkgId);
            if (zhimouStatus == 1) up.set(LiveStream::getZhimouStartTime, LocalDateTime.now());
            if (zhimouStatus == 2) up.set(LiveStream::getZhimouStopTime, LocalDateTime.now());
            liveStreamMapper.update(null, up);
        } catch (Exception e) {
            log.warn("[知眸-更新直播表知眸字段] 异常 liveStreamId={}", liveStreamId, e);
        }
    }

    private void saveZhimouTask(LiveStream live, String zhimouTaskId, String appId, String pkgId,
                                String inputUrl, int status, String reqJson, String respJson, String lastErr, String waylineUuid) {
        try {
            ZhimouTask t = new ZhimouTask();
            t.setLiveStreamId(live.getId());
            t.setZhimouTaskId(StrUtil.isNotBlank(zhimouTaskId) ? zhimouTaskId : ("tmp_" + live.getId() + "_" + System.currentTimeMillis()));
            t.setGatewaySn(live.getGatewaySn());
            t.setAircraftSn(live.getAircraftSn());
            t.setCameraIndex(live.getCameraIndex());
            t.setAppId(appId);
            t.setApplicationPackageId(pkgId);
            t.setBusinessId(String.valueOf(live.getId()));
            t.setMode("video");
            t.setInputUrl(inputUrl);
            t.setReportUrl(properties.getOpenapi().getCallbackUrl());
            t.setStatus(status);
            t.setStatusMessage(lastErr);
            if (status == 1) t.setStartTime(LocalDateTime.now());
            if (status == 2) t.setStopTime(LocalDateTime.now());
            t.setRequestJson(reqJson);
            t.setResponseJson(respJson);
            t.setLastError(lastErr);
            t.setWaylineUuid(waylineUuid);
            this.save(t);
        } catch (Exception e) {
            log.warn("[知眸-写zhimou_task表] 异常", e);
        }
    }

    // 调用知眸API失败时的回填(原来的createTask返回空分支),也要使用appIdsJoinedStr
    private void handleCreateTaskFail(LiveStream liveStream, String appIdsStr, String pkgId, String inputUrl, String reqJson, String errMsg) {
        updateLiveStreamZhimouStatus(liveStream.getId(), 3, null, errMsg, appIdsStr, pkgId);
        saveZhimouTask(liveStream, null, appIdsStr, pkgId, inputUrl, 3, reqJson, null, errMsg, null);
    }

    /** 查询飞机当前飞行任务的航线UUID */
    private String queryWaylineUuid(String aircraftSn) {
        if (StrUtil.isBlank(aircraftSn)) return null;
        
        try {
            // 优先从device_realtime_status表查询(OSD消息中可能包含航线UUID)
            LambdaQueryWrapper<com.dji.sikong.entity.DeviceRealtimeStatus> statusQw = new LambdaQueryWrapper<>();
            statusQw.eq(com.dji.sikong.entity.DeviceRealtimeStatus::getDeviceSn, aircraftSn);
            statusQw.orderByDesc(com.dji.sikong.entity.DeviceRealtimeStatus::getId).last("LIMIT 1");
            List<com.dji.sikong.entity.DeviceRealtimeStatus> statusList = realtimeStatusService.list(statusQw);
            if (!CollectionUtils.isEmpty(statusList)) {
                com.dji.sikong.entity.DeviceRealtimeStatus status = statusList.get(0);
                if (StrUtil.isNotBlank(status.getWaylineUuid())) {
                    return status.getWaylineUuid();
                }
            }
        } catch (Exception e) {
            log.warn("[知眸-查询航线UUID] 查询device_realtime_status失败: aircraftSn={}, error={}", aircraftSn, e.getMessage());
        }
        
        try {
            // 其次从本地flight_task表查询
            LambdaQueryWrapper<com.dji.sikong.entity.FlightTask> ftQw = new LambdaQueryWrapper<>();
            ftQw.eq(com.dji.sikong.entity.FlightTask::getAircraftSn, aircraftSn);
            ftQw.in(com.dji.sikong.entity.FlightTask::getTaskStatus, 0, 1);
            ftQw.orderByDesc(com.dji.sikong.entity.FlightTask::getId).last("LIMIT 1");
            List<com.dji.sikong.entity.FlightTask> ftList = flightTaskMapper.selectList(ftQw);
            if (!CollectionUtils.isEmpty(ftList)) {
                com.dji.sikong.entity.FlightTask ft = ftList.get(0);
                if (StrUtil.isNotBlank(ft.getWaylineUuid())) {
                    return ft.getWaylineUuid();
                }
            }
        } catch (Exception e) {
            log.warn("[知眸-查询航线UUID] 本地查询flight_task失败: aircraftSn={}, error={}", aircraftSn, e.getMessage());
        }
        
        try {
            // 从大疆OpenAPI查询最近飞行任务列表
            String projectUuid = openApiProperties.getDefaultProjectUuid();
            JsonNode recentTasks = djiOpenApiClient.getRecentFlightTasks(projectUuid, aircraftSn);
            if (recentTasks != null && recentTasks.has("data")) {
                JsonNode dataNode = recentTasks.get("data");
                if (dataNode.has(aircraftSn)) {
                    JsonNode taskList = dataNode.get(aircraftSn);
                    if (taskList.isArray() && taskList.size() > 0) {
                        JsonNode taskNode = taskList.get(0);
                        String wUuid = taskNode.has("wayline_uuid") ? taskNode.get("wayline_uuid").asText(null) : null;
                        if (StrUtil.isNotBlank(wUuid)) {
                            return wUuid;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[知眸-查询航线UUID] OpenAPI查询最近飞行任务失败: aircraftSn={}, error={}", aircraftSn, e.getMessage());
        }
        
        try {
            // 从大疆OpenAPI飞行任务列表查询
            String projectUuid = openApiProperties.getDefaultProjectUuid();
            JsonNode tasksNode = djiOpenApiClient.listFlightTasks(projectUuid);
            if (tasksNode != null && tasksNode.has("data") && tasksNode.get("data").has("list")) {
                JsonNode listNode = tasksNode.get("data").get("list");
                if (listNode.isArray()) {
                    for (JsonNode taskNode : listNode) {
                        String taskDeviceSn = taskNode.has("device_sn") ? taskNode.get("device_sn").asText(null) : null;
                        if (aircraftSn.equals(taskDeviceSn)) {
                            Integer taskStatus = taskNode.has("status") ? taskNode.get("status").asInt(-1) : -1;
                            if (taskStatus == 0 || taskStatus == 1) {
                                String wUuid = taskNode.has("wayline_uuid") ? taskNode.get("wayline_uuid").asText(null) : null;
                                if (StrUtil.isNotBlank(wUuid)) {
                                    return wUuid;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[知眸-查询航线UUID] OpenAPI查询飞行任务列表失败: aircraftSn={}, error={}", aircraftSn, e.getMessage());
        }
        
        try {
            // 通过机场绑定关系查询
            com.dji.sikong.entity.Device aircraft = deviceMapper.selectOne(
                    new LambdaQueryWrapper<com.dji.sikong.entity.Device>()
                            .eq(com.dji.sikong.entity.Device::getSn, aircraftSn)
                            .last("LIMIT 1"));
            
            if (aircraft != null && StrUtil.isNotBlank(aircraft.getGatewaySn())) {
                String airportSn = aircraft.getGatewaySn();
                LambdaQueryWrapper<com.dji.sikong.entity.DeviceRealtimeStatus> airportStatusQw = new LambdaQueryWrapper<>();
                airportStatusQw.eq(com.dji.sikong.entity.DeviceRealtimeStatus::getDeviceSn, airportSn);
                airportStatusQw.orderByDesc(com.dji.sikong.entity.DeviceRealtimeStatus::getId).last("LIMIT 1");
                List<com.dji.sikong.entity.DeviceRealtimeStatus> airportStatusList = realtimeStatusService.list(airportStatusQw);
                
                if (!CollectionUtils.isEmpty(airportStatusList)) {
                    com.dji.sikong.entity.DeviceRealtimeStatus airportStatus = airportStatusList.get(0);
                    if (StrUtil.isNotBlank(airportStatus.getWaylineUuid())) {
                        return airportStatus.getWaylineUuid();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[知眸-查询航线UUID] 通过机场绑定关系查询失败: aircraftSn={}, error={}", aircraftSn, e.getMessage());
        }
        
        return null;
    }

    @Override
    public boolean closeZhimouTaskByAircraftSn(String aircraftSn) {
        if (StrUtil.isBlank(aircraftSn)) return false;
        LambdaQueryWrapper<ZhimouTask> qw = new LambdaQueryWrapper<>();
        qw.eq(ZhimouTask::getAircraftSn, aircraftSn).in(ZhimouTask::getStatus, 0, 1);
        List<ZhimouTask> list = this.list(qw);
        if (CollectionUtils.isEmpty(list)) {
            log.info("[知眸-关任务] 飞机{}无活跃知眸任务", aircraftSn);
        }
        System.out.println("[知眸-关任务(飞机)] 飞机=" + aircraftSn + " 关到" + (list != null ? list.size() : 0) + "个zhimou_task任务");
        boolean allOk = true;
        if (!CollectionUtils.isEmpty(list)) {
            for (ZhimouTask t : list) {
                if (!doCloseZhimouTask(t)) allOk = false;
            }
        }
        // ✈️ 修复无限循环: 只更新【已停止】的直播(status!=1)的zhimou_status→2
        //    ❌ 绝对不能碰正在运行中的直播(status=1), 否则会把刚开播的直播知眸任务直接关闭,导致永远创建不起来!
        LambdaQueryWrapper<LiveStream> lsq2 = new LambdaQueryWrapper<>();
        lsq2.eq(LiveStream::getAircraftSn, aircraftSn)
            .in(LiveStream::getZhimouStatus, 0, 1, 3)
            .ne(LiveStream::getStatus, 1);  // ✅ 关键: 只更新已停止的直播,不碰运行中的
        List<LiveStream> lsList2 = liveStreamMapper.selectList(lsq2);
        if (!CollectionUtils.isEmpty(lsList2)) {
            for (LiveStream ls : lsList2) {
                updateLiveStreamZhimouStatus(ls.getId(), 2, null, null, null, null);
            }
            System.out.println("[知眸-关任务(飞机)-同步live_stream🔄] 飞机=" + aircraftSn + " 同步更新了" + lsList2.size() + "条已停直播的zhimou_status→2(跳过运行中的直播,避免误杀)");
        }
        return allOk;
    }

    @Override
    public boolean closeZhimouTaskByLiveStreamId(Long liveStreamId) {
        if (liveStreamId == null) return false;
        LambdaQueryWrapper<ZhimouTask> qw = new LambdaQueryWrapper<>();
        qw.eq(ZhimouTask::getLiveStreamId, liveStreamId).in(ZhimouTask::getStatus, 0, 1);
        List<ZhimouTask> list = this.list(qw);
        System.out.println("[知眸-关任务(直播)] liveId=" + liveStreamId + " 关到" + (list != null ? list.size() : 0) + "个zhimou_task任务");
        boolean ok = true;
        if (!CollectionUtils.isEmpty(list)) {
            for (ZhimouTask t : list) {
                if (!doCloseZhimouTask(t)) ok = false;
            }
        }
        // ✈️ 同样强制更新这个 liveStreamId 的 zhimou_status→2, 避免扫尾补偿无限循环
        updateLiveStreamZhimouStatus(liveStreamId, ok ? 2 : 4, null, ok ? null : "调用知眸关任务失败", null, null);
        return ok;
    }

    private boolean doCloseZhimouTask(ZhimouTask t) {
        try {
            log.info("[知眸-关任务] 关闭task_id={}, aircraftSn={}", t.getZhimouTaskId(), t.getAircraftSn());
            JsonNode resp = zhimouOpenApiClient.deleteTask(t.getZhimouTaskId());
            if (resp == null) {
                log.warn("[知眸-关任务] 返回空, 视为已关闭 task_id={}", t.getZhimouTaskId());
            }
            LambdaUpdateWrapper<ZhimouTask> up = new LambdaUpdateWrapper<>();
            up.eq(ZhimouTask::getId, t.getId());
            up.set(ZhimouTask::getStatus, 2);
            up.set(ZhimouTask::getStatusMessage, "");
            up.set(ZhimouTask::getStopTime, LocalDateTime.now());
            this.update(up);
            return true;
        } catch (Exception e) {
            log.error("[知眸-关任务] 异常 task_id={}", t.getZhimouTaskId(), e);
            LambdaUpdateWrapper<ZhimouTask> up = new LambdaUpdateWrapper<>();
            up.eq(ZhimouTask::getId, t.getId());
            up.set(ZhimouTask::getStatus, 4);
            up.set(ZhimouTask::getStatusMessage, e.getMessage());
            this.update(up);
            return false;
        }
    }

    @Override
    public void cleanupZombieTasksOnStartup() {
        try {
            JsonNode tasksNode = zhimouOpenApiClient.listTasks();
            if (tasksNode == null) return;
            Set<String> remoteRunningTaskIds = new HashSet<>();
            JsonNode arr = tasksNode.has("tasks") ? tasksNode.get("tasks") : tasksNode;
            if (arr != null && arr.isArray()) {
                for (JsonNode j : arr) {
                    String id = j.path("task_id").asText(null);
                    if (StrUtil.isNotBlank(id)) remoteRunningTaskIds.add(id);
                }
            }
            LambdaQueryWrapper<ZhimouTask> qw = new LambdaQueryWrapper<>();
            qw.in(ZhimouTask::getStatus, 0, 1);
            List<ZhimouTask> local = this.list(qw);
            for (ZhimouTask t : local) {
                if (!remoteRunningTaskIds.contains(t.getZhimouTaskId())) {
                    log.warn("[知眸-清理僵尸] 本地状态={},但知眸侧无此任务,强制标记已关闭: {}", t.getStatus(), t.getZhimouTaskId());
                    LambdaUpdateWrapper<ZhimouTask> up = new LambdaUpdateWrapper<>();
                    up.eq(ZhimouTask::getId, t.getId()).set(ZhimouTask::getStatus, 2).set(ZhimouTask::getStopTime, LocalDateTime.now());
                    this.update(up);
                    if (t.getLiveStreamId() != null) {
                        updateLiveStreamZhimouStatus(t.getLiveStreamId(), 2, null, "服务重启清理", null, null);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[知眸-清理僵尸任务] 异常", e);
        }
    }

    @Override
    public int cleanupOldHistoryRecords(int retainHours) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(retainHours);
            log.info("[知眸-历史清理] 开始清理{}小时前的记录, 截止时间={}", retainHours, cutoff);

            int liveDeleted = liveStreamMapper.delete(new LambdaQueryWrapper<LiveStream>()
                    .lt(LiveStream::getCreateTime, cutoff));

            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ZhimouTask> zUp = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            zUp.lt(ZhimouTask::getCreateTime, cutoff);
            long taskCnt = this.count(new LambdaQueryWrapper<ZhimouTask>().lt(ZhimouTask::getCreateTime, cutoff));
            int taskDeleted = (int) taskCnt;
            this.remove(zUp);

            log.info("[知眸-历史清理] 完成: 直播流删除{}条, 知眸任务删除{}条", liveDeleted, taskDeleted);
            return liveDeleted + taskDeleted;
        } catch (Exception e) {
            log.warn("[知眸-历史清理] 异常(不影响主流程)", e);
            return 0;
        }
    }

    // ================================================================
    // 飞行位置上报(OSD变动时触发)
    // ================================================================
    @Override
    public void reportFlightIfNeeded(String aircraftSn, Double latitude, Double longitude,
                                     Double altitude, Double height, Double gimbalPitch,
                                     Double zoomFactor, Long timestampMs) {
        if (StrUtil.isBlank(aircraftSn) || latitude == null || longitude == null) return;
        long interval = properties.getFlightReportIntervalMs();
        if (interval > 0) {
            try {
                String key = LAST_FLIGHT_REPORT_TS_KEY + aircraftSn;
                String last = stringRedisTemplate.opsForValue().get(key);
                long now = System.currentTimeMillis();
                long lastTs = last != null ? Long.parseLong(last) : 0L;
                if (now - lastTs < interval) return;
                stringRedisTemplate.opsForValue().set(key, String.valueOf(now), 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.debug("[知眸-上报GPS] Redis节流异常, 忽略继续上报", e);
            }
        }
        try {
            LambdaQueryWrapper<ZhimouTask> qw = new LambdaQueryWrapper<>();
            qw.eq(ZhimouTask::getAircraftSn, aircraftSn).eq(ZhimouTask::getStatus, 1);
            List<ZhimouTask> list = this.list(qw);
            if (CollectionUtils.isEmpty(list)) return;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("latitude", latitude);
            body.put("longitude", longitude);
            if (altitude != null)   body.put("altitude", altitude);
            if (height != null)     body.put("height", height);
            if (gimbalPitch != null)body.put("gimbal_pitch", gimbalPitch);
            if (zoomFactor != null) body.put("zoom_factor", zoomFactor);
            body.put("timestamp", timestampMs != null ? timestampMs : System.currentTimeMillis());
            for (ZhimouTask t : list) {
                zhimouOpenApiClient.reportFlight(t.getZhimouTaskId(), body);
            }
        } catch (Exception e) {
            log.debug("[知眸-上报飞行位置] 异常(不影响主流程)", e);
        }
    }

    // ================================================================
    // 回调处理(核心)
    // ================================================================
    @Override
    public ZhimouDetectionResult handleCallback(JsonNode root) {
        if (root == null) return null;
        String rawJson = JSONUtil.toJsonStr(root);
        log.info("[知眸-回调] 收到消息: {}", rawJson);

        // ============================================================
        // ✅第一步: 判断回调格式类型(新=真实知眸  vs  旧=模拟/旧版)
        //   新格式特征: 顶层有 event="message" + data.apps[]数组存在
        //   旧格式特征: 顶层直接有 image / rules / flight 节点
        // ============================================================
        boolean isNewFormat = "message".equals(root.path("event").asText(null))
                && root.has("data") && root.path("data").has("apps") && root.path("data").path("apps").isArray();

        String zhimouTaskId = root.path("task_id").asText(null);
        String businessId = root.path("business_id").asText(null);
        // 兼容: 新格式 device_id | 旧格式 device
        String device = StrUtil.blankToDefault(root.path("device_id").asText(null), root.path("device").asText(null));
        // 兼容: 新格式顶层 timestamp | 旧格式 event_timestamp_ms | 当前时间兜底
        long eventTs = root.has("timestamp") && root.path("timestamp").isNumber()
                ? root.path("timestamp").asLong()
                : (root.has("event_timestamp_ms") ? root.path("event_timestamp_ms").asLong() : System.currentTimeMillis());

        // 找关联的task
        ZhimouTask zhimouTask = null;
        LiveStream liveStream = null;
        if (StrUtil.isNotBlank(zhimouTaskId)) {
            zhimouTask = this.getOne(new LambdaQueryWrapper<ZhimouTask>().eq(ZhimouTask::getZhimouTaskId, zhimouTaskId).last("LIMIT 1"));
        }
        if (zhimouTask == null && StrUtil.isNotBlank(businessId)) {
            try {
                long lsId = Long.parseLong(businessId);
                liveStream = liveStreamMapper.selectById(lsId);
                if (liveStream != null && StrUtil.isNotBlank(liveStream.getZhimouTaskId())) {
                    zhimouTask = this.getOne(new LambdaQueryWrapper<ZhimouTask>().eq(ZhimouTask::getZhimouTaskId, liveStream.getZhimouTaskId()).last("LIMIT 1"));
                }
            } catch (Exception ignore) {}
        }
        if (zhimouTask == null && StrUtil.isNotBlank(device)) {
            zhimouTask = this.getOne(new LambdaQueryWrapper<ZhimouTask>().eq(ZhimouTask::getAircraftSn, device).orderByDesc(ZhimouTask::getId).last("LIMIT 1"));
        }

        // ============================================================
        // ✅ 二期新增: 按 zhimouTask.mode 分流
        //   mode=image → 写入 zhimou_image_detection 表 (二期图片识别)
        //   其他(null/video) → 写入 zhimou_detection_result 表 (一期视频识别, 原逻辑)
        // ============================================================
        if (zhimouTask != null && "image".equalsIgnoreCase(zhimouTask.getMode())) {
            return handleImageCallback(root, zhimouTask, rawJson, zhimouTaskId, businessId, device, eventTs);
        }

        // ===== 1. 图片处理 =====
        String imageRelativePath = null;
        String imageFormat = "png";
        Integer imageWidth = null, imageHeight = null;
        String imageOriginUrl = null;
        Integer imageHasOsd = 1;
        byte[] imgBytes = null;
        String fmt = null;

        if (isNewFormat) {
            // ================================================================
            // 【新格式: 真实知眸生产回调】 — data.frame + data.apps[]
            //   image:  apps[0].image = "/openapi/taskid/messages/appid/ts.jpg" (相对URL, 需拼openapi.baseUrl)
            //   宽高:   data.frame.width/height
            //   frameId:data.frame.id
            // ================================================================
            JsonNode dataNode = root.path("data");
            JsonNode frameNode = dataNode.path("frame");
            if (!frameNode.isMissingNode() && !frameNode.isNull()) {
                if (frameNode.has("width") && frameNode.path("width").isNumber())  imageWidth = frameNode.get("width").asInt();
                if (frameNode.has("height") && frameNode.path("height").isNumber()) imageHeight = frameNode.get("height").asInt();
                if (frameNode.has("enhance") && frameNode.path("enhance").isNumber()) imageHasOsd = frameNode.get("enhance").asInt(1);
            }

            // 遍历 apps[] 找第一个非空的 image URL 下载
            JsonNode appsNode = dataNode.path("apps");
            String baseUrl = (properties != null && properties.getOpenapi() != null)
                    ? StrUtil.trimToEmpty(properties.getOpenapi().getBaseUrl()) : "";
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            if (appsNode.isArray() && appsNode.size() > 0) {
                for (JsonNode app : appsNode) {
                    String appImage = app.path("image").asText(null);
                    if (StrUtil.isBlank(appImage)) continue;
                    // 相对URL → 拼 baseUrl
                    String fullUrl = appImage;
                    if (appImage.startsWith("/")) {
                        fullUrl = baseUrl + appImage;
                    } else if (!appImage.toLowerCase().startsWith("http")) {
                        fullUrl = baseUrl + "/" + appImage;
                    }
                    imageOriginUrl = fullUrl;
                    log.info("[知眸-回调-新格式] 下载识别图片 apps[].image={} fullUrl={}", appImage, fullUrl);
                    try {
                        imgBytes = HttpUtil.downloadBytes(fullUrl);
                        if (imgBytes != null && imgBytes.length > 0) {
                            log.info("[知眸-回调-新格式] 图片下载成功 size={}, 准备强制转PNG保存", imgBytes.length);
                            // 从URL里解析原始扩展名(仅作日志参考,最终一律转PNG)
                            int dot = appImage.lastIndexOf('.');
                            if (dot > 0 && dot < appImage.length() - 1) fmt = appImage.substring(dot + 1).toLowerCase(Locale.ROOT);
                            break;
                        } else {
                            log.warn("[知眸-回调-新格式] 图片下载返回空字节 fullUrl={}, 尝试下一个app的image", fullUrl);
                        }
                    } catch (Exception e) {
                        log.warn("[知眸-回调-新格式] 下载图片失败 fullUrl={}, err={}, 尝试下一个app的image", fullUrl, e.getMessage());
                    }
                }
            }
            if (imgBytes == null || imgBytes.length == 0) {
                log.warn("[知眸-回调-新格式] apps[] 中未找到任何可下载的图片");
            }
        } else {
            // ================================================================
            // 【旧格式: 模拟回调/旧版API】 — 顶层 image{image_base64|image_url|data|...}
            // ================================================================
            JsonNode imageNode = root.path("image");
            if (imageNode != null && !imageNode.isMissingNode() && !imageNode.isNull()) {
                fmt = imageNode.path("format").asText(null);
                if (StrUtil.isNotBlank(fmt)) imageFormat = fmt;
                if (imageNode.has("width")) imageWidth = imageNode.get("width").asInt();
                else if (imageNode.has("w")) imageWidth = imageNode.get("w").asInt();
                if (imageNode.has("height")) imageHeight = imageNode.get("height").asInt();
                else if (imageNode.has("h")) imageHeight = imageNode.get("h").asInt();
                imageHasOsd = imageNode.path("has_osd").asInt(1);
                imageOriginUrl = imageNode.path("image_url").asText(null);
                if (StrUtil.isBlank(imageOriginUrl)) imageOriginUrl = imageNode.path("url").asText(null);
                if (StrUtil.isBlank(imageOriginUrl)) imageOriginUrl = imageNode.path("download_url").asText(null);

                // Base64图片内容:兼容多种命名
                String base64 = imageNode.path("image_base64").asText(null);
                if (StrUtil.isBlank(base64)) base64 = imageNode.path("base64").asText(null);
                if (StrUtil.isBlank(base64)) base64 = imageNode.path("data").asText(null);
                if (StrUtil.isBlank(base64)) base64 = imageNode.path("content").asText(null);
                if (StrUtil.isNotBlank(base64)) {
                    try {
                        imgBytes = Base64.decode(base64);
                    } catch (Exception e) {
                        log.warn("[知眸-回调] Base64解码失败(尝试去掉data:image前缀后重试): {}", e.getMessage());
                        try {
                            int commaIdx = base64.indexOf(',');
                            if (commaIdx > 0 && commaIdx < base64.length() - 4) {
                                imgBytes = Base64.decode(base64.substring(commaIdx + 1));
                            }
                        } catch (Exception ex) {
                            log.warn("[知眸-回调] Base64二次解码失败", ex);
                        }
                    }
                }
                // Base64为空 → 尝试HTTP下载
                if (imgBytes == null && StrUtil.isNotBlank(imageOriginUrl)) {
                    try {
                        imgBytes = HttpUtil.downloadBytes(imageOriginUrl);
                    } catch (Exception e) {
                        log.warn("[知眸-回调] 下载图片失败 url={}, err={}", imageOriginUrl, e.getMessage());
                    }
                }
            } else {
                log.info("[知眸-回调-旧格式] 消息中无顶层image节点, 仅保存结构化识别数据和GPS");
            }
        }

        // 拿到原始图片bytes → 强制ImageIO转码为PNG再保存
        if (imgBytes != null && imgBytes.length > 0) {
            byte[] pngBytes = convertToPngBytes(imgBytes);
            if (pngBytes != null && pngBytes.length > 0) {
                imageRelativePath = saveImageToLocal(pngBytes, "png", eventTs);
                log.info("[知眸-回调] 图片保存成功(强制PNG), 原始format={}, 最终相对路径={}", fmt, imageRelativePath);
                imgBytes = pngBytes;
                imageFormat = "png";
                if ((imageWidth == null || imageHeight == null)) {
                    try {
                        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(pngBytes));
                        if (bi != null) {
                            if (imageWidth == null) imageWidth = bi.getWidth();
                            if (imageHeight == null) imageHeight = bi.getHeight();
                        }
                    } catch (Exception ignore) {}
                }
            } else {
                log.warn("[知眸-回调] ImageIO转PNG失败, 按原始字节保存非强制");
                imageRelativePath = saveImageToLocal(imgBytes, (StrUtil.isNotBlank(fmt) ? fmt : imageFormat), eventTs);
                if (StrUtil.isNotBlank(fmt)) imageFormat = fmt;
            }
        } else if (!isNewFormat) {
            log.warn("[知眸-回调] 未获取到图片字节数组(base64/下载均为空)");
        }

        // ===== 2. 目标识别结果解析(新旧格式分别解析) =====
        List<String> ruleIds = new ArrayList<>();
        List<String> ruleNames = new ArrayList<>();
        int totalHit = 0;
        // 直接保存JSON字符串(实体字段接受String), 避免hutool<->jackson类型不兼容导致编译级错误
        String targetsJsonStr = null;

        if (isNewFormat) {
            // 新格式: data.apps[] -> 每个 app 一条规则, 命中数=objects.size
            JsonNode appsNode = root.path("data").path("apps");
            List<Map<String, Object>> rulesList = new ArrayList<>();
            if (appsNode.isArray()) {
                for (JsonNode app : appsNode) {
                    String appId = app.path("app_id").asText(null);
                    String appName = app.path("app_name").asText(null);
                    JsonNode objectsNode = app.path("objects");
                    int cnt = (objectsNode != null && objectsNode.isArray()) ? objectsNode.size() : 0;
                    totalHit += cnt;
                    if (StrUtil.isNotBlank(appId)) ruleIds.add(appId);
                    if (StrUtil.isNotBlank(appName)) ruleNames.add(appName);
                    // 转成和旧格式 rules[] 一样结构, 便于统一写入targets_json
                    Map<String, Object> ruleMap = new LinkedHashMap<>();
                    ruleMap.put("rule_id", appId);
                    ruleMap.put("rule_name", appName);
                    ruleMap.put("hit_count", cnt);
                    List<Object> targetsList = new ArrayList<>();
                    if (objectsNode != null && objectsNode.isArray()) {
                        for (JsonNode obj : objectsNode) {
                            Map<String, Object> target = new LinkedHashMap<>();
                            target.put("target_id", obj.path("track_id").asText(null));
                            target.put("class_id", obj.path("class_id").asText(null));
                            target.put("class", obj.path("name").asText(null));
                            target.put("confidence", obj.path("confidence").asDouble(0));
                            target.put("color", obj.path("color").asText(null));
                            target.put("description", obj.path("description").asText(null));
                            if (obj.has("x") && obj.has("y")) {
                                target.put("x", obj.get("x").asInt());
                                target.put("y", obj.get("y").asInt());
                            }
                            targetsList.add(target);
                        }
                    }
                    ruleMap.put("targets", targetsList);
                    rulesList.add(ruleMap);
                }
            }
            targetsJsonStr = JSONUtil.toJsonStr(rulesList);
        } else {
            // 旧格式: 顶层 rules[] -> rule_id, rule_name, targets[]
            JsonNode rulesNode = root.path("rules");
            if (rulesNode.isArray()) {
                for (JsonNode r : rulesNode) {
                    String rid = r.path("rule_id").asText(null);
                    String rname = r.path("rule_name").asText(null);
                    int cnt = r.path("hit_count").asInt(0);
                    JsonNode targetsNode = r.path("targets");
                    if (targetsNode.isArray() && cnt == 0) cnt = targetsNode.size();
                    totalHit += cnt;
                    if (StrUtil.isNotBlank(rid)) ruleIds.add(rid);
                    if (StrUtil.isNotBlank(rname)) ruleNames.add(rname);
                }
            }
            if (rulesNode != null && !rulesNode.isMissingNode() && !rulesNode.isNull() && rulesNode.isArray()) {
                targetsJsonStr = rulesNode.toString();
            }
        }

        // ===== 3. GPS/时间 =====
        BigDecimal latitude = null, longitude = null, altitude = null, height = null, gimbalPitch = null, zoomFactor = null;
        if (isNewFormat) {
            // 新格式回调里目前没有flight/GPS字段 -> 下面统一从OSD缓存补
            JsonNode gpsFromNew = root.path("data").has("gps") ? root.path("data").path("gps") : null;
            if (gpsFromNew != null && !gpsFromNew.isNull() && !gpsFromNew.isMissingNode()) {
                latitude  = gpsFromNew.has("latitude")  ? toDecimal(gpsFromNew.path("latitude").asText(null))  : null;
                longitude = gpsFromNew.has("longitude") ? toDecimal(gpsFromNew.path("longitude").asText(null)) : null;
                altitude  = gpsFromNew.has("altitude")  ? toDecimal(gpsFromNew.path("altitude").asText(null))  : null;
                height    = gpsFromNew.has("height")    ? toDecimal(gpsFromNew.path("height").asText(null))    : null;
            }
        } else {
            JsonNode flightNode = root.path("flight");
            latitude    = flightNode.has("latitude")    ? toDecimal(flightNode.path("latitude").asText(null))    : null;
            longitude   = flightNode.has("longitude")   ? toDecimal(flightNode.path("longitude").asText(null))   : null;
            altitude    = flightNode.has("altitude")    ? toDecimal(flightNode.path("altitude").asText(null))    : null;
            height      = flightNode.has("height")      ? toDecimal(flightNode.path("height").asText(null))      : null;
            gimbalPitch = flightNode.has("gimbal_pitch")? toDecimal(flightNode.path("gimbal_pitch").asText(null)): null;
            zoomFactor  = flightNode.has("zoom_factor") ? toDecimal(flightNode.path("zoom_factor").asText(null)) : null;
        }
        // 若回调里没GPS -> 统一从最近的OSD缓存里补一份(飞机正在飞时100%有OSD数据)
        if ((latitude == null || longitude == null) && StrUtil.isNotBlank(device)) {
            Double[] gps = findLatestGpsFromOsd(device);
            if (gps != null) {
                latitude = BigDecimal.valueOf(gps[0]);
                longitude = BigDecimal.valueOf(gps[1]);
                height = gps[2] != null ? BigDecimal.valueOf(gps[2]) : null;
            }
        }

        LocalDateTime eventTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(eventTs), ZoneId.systemDefault());
        Long frameId = null;
        if (isNewFormat) {
            long fid = root.path("data").path("frame").path("id").asLong(-1);
            if (fid > 0) frameId = fid;
        } else {
            long fid = root.path("frame").path("id").asLong(-1);
            if (fid > 0) frameId = fid;
        }

        // 保存条件(不变)
        boolean shouldSave = (imageRelativePath != null && !imageRelativePath.isEmpty()) || totalHit > 0;
        if (!shouldSave) {
            log.debug("[知眸-回调] 无图片且无命中目标(totalHit=0, imageRelativePath={}), 视为心跳跳过写明细表 taskId={}",
                    imageRelativePath, zhimouTaskId);
            return null;
        }

        // ===== 4. 写 MySQL =====
        ZhimouDetectionResult dr = new ZhimouDetectionResult();
        dr.setZhimouTaskId(zhimouTaskId);
        if (zhimouTask != null) {
            dr.setTaskId(zhimouTask.getId());
            dr.setLiveStreamId(zhimouTask.getLiveStreamId());
            if (StrUtil.isBlank(device)) device = zhimouTask.getAircraftSn();
        } else if (liveStream != null) {
            dr.setLiveStreamId(liveStream.getId());
        }
        dr.setAircraftSn(device);
        // 从ZhimouTask获取航线UUID(一次飞行任务期间固定不变，在任务创建时查询)
        if (zhimouTask != null && StrUtil.isNotBlank(zhimouTask.getWaylineUuid())) {
            dr.setWaylineUuid(zhimouTask.getWaylineUuid());
            log.info("[知眸-回调] 从ZhimouTask获取航线UUID: zhimouTaskId={}, waylineUuid={}", zhimouTaskId, zhimouTask.getWaylineUuid());
        } else {
            dr.setWaylineUuid("手动飞行");
        }
        dr.setBusinessId(businessId);
        dr.setFrameId(frameId);
        dr.setImageRelativePath(imageRelativePath != null ? imageRelativePath : "");
        dr.setImageFormat(imageFormat);
        dr.setImageWidth(imageWidth);
        dr.setImageHeight(imageHeight);
        dr.setImageUrlOrigin(imageOriginUrl);
        dr.setImageHasOsd(imageHasOsd);
        dr.setRuleId(StrUtil.join(",", ruleIds));
        dr.setRuleName(StrUtil.join(",", ruleNames));
        dr.setHitCount(totalHit);
        if (StrUtil.isNotBlank(targetsJsonStr)) {
            dr.setTargetsJson(targetsJsonStr);
        }
        dr.setLatitude(latitude);
        dr.setLongitude(longitude);
        dr.setAltitude(altitude);
        dr.setHeight(height);
        dr.setGimbalPitch(gimbalPitch);
        dr.setZoomFactor(zoomFactor);
        dr.setEventTimestampMs(eventTs);
        dr.setEventTime(eventTime);
        dr.setRawCallbackJson(rawJson);
        detectionResultMapper.insert(dr);
        log.info("[知眸-回调] 写明细表成功 id={}, 新格式?={}, imagePath={}, hit={}, rules={}",
                dr.getId(), isNewFormat ? "✅新" : "旧", imageRelativePath, totalHit, ruleNames);

        // ===== 5. MQTT推送给前端(页面实时刷新) =====
        pushAlertViaMqtt(dr);

        return dr;
    }

    // ================================================================
    // 二期新增: 知眸图片任务回调处理 (写入 zhimou_image_detection 表)
    // 通过 frame.id 关联回调时的 imageId, 从 Redis 反查元数据
    // ================================================================
    private ZhimouDetectionResult handleImageCallback(JsonNode root, ZhimouTask zhimouTask,
                                                      String rawJson, String zhimouTaskId,
                                                      String businessId, String device, long eventTs) {
        // 1. 解析 frame.id (知眸 frame.id 从0开始递增, 按收图顺序)
        JsonNode frameNode = root.path("data").path("frame");
        long frameId = frameNode.path("id").asLong(-1);
        if (frameId < 0) frameId = frameNode.path("image_id").asLong(-1);

        // 2. 从 Redis 反查元数据 (local_image_name, local_image_path, lat, lon, alt)
        //    key: zhimou:img:meta:{taskId}:{frameIdx}  (frameIdx 从0开始, 对应知眸 frame.id)
        BigDecimal lat = null, lon = null, alt = null;
        String localImageName = null, localImagePath = null;
        if (frameId >= 0 && StrUtil.isNotBlank(zhimouTaskId)) {
            String metaKey = "zhimou:img:meta:" + zhimouTaskId + ":" + frameId;
            Map<Object, Object> meta = stringRedisTemplate.opsForHash().entries(metaKey);
            Object nm = meta.get("local_image_name");
            Object ip = meta.get("local_image_path");
            localImageName = nm != null ? nm.toString() : null;
            localImagePath = ip != null ? ip.toString() : null;
            lat = parseBigDecimal(meta.get("latitude"));
            lon = parseBigDecimal(meta.get("longitude"));
            alt = parseBigDecimal(meta.get("altitude"));
            log.info("[知眸-图片回调] 反查Redis meta key={}, frameId={}, localImageName={}, lat={}, lon={}, alt={}",
                    metaKey, frameId, localImageName, lat, lon, alt);
        } else {
            log.warn("[知眸-图片回调] frame.id/image_id 解析失败, 无法反查元数据, 仅依赖回调内容入库");
        }

        // 3. 解析回调里的图片路径和宽高
        String zhimouImagePath = frameNode.path("image").asText(null);
        Integer imageWidth = frameNode.has("width") && frameNode.path("width").isNumber() ? frameNode.get("width").asInt() : null;
        Integer imageHeight = frameNode.has("height") && frameNode.path("height").isNumber() ? frameNode.get("height").asInt() : null;
        Long frameIdBox = frameId >= 0 ? frameId : null;
        Integer frameEnhance = frameNode.has("enhance") && frameNode.path("enhance").isNumber() ? frameNode.get("enhance").asInt() : null;

        // 4. 解析 apps[] → appId, appName, zhimouAppImage, targetsJson, totalHit
        JsonNode appsNode = root.path("data").path("apps");
        int totalHit = 0;
        String targetsJsonStr = null;
        String appId = null, appName = null, zhimouAppImage = null;
        String zapkId = root.path("zapk_id").asText(null);
        if (appsNode.isArray() && appsNode.size() > 0) {
            JsonNode app0 = appsNode.get(0);
            appId = app0.path("app_id").asText(null);
            appName = app0.path("app_name").asText(null);
            zhimouAppImage = app0.path("image").asText(null);
            JsonNode objectsNode = app0.path("objects");
            totalHit = (objectsNode != null && objectsNode.isArray()) ? objectsNode.size() : 0;
            // targets_json 保留 apps[] 完整原文 (排错/审计)
            targetsJsonStr = appsNode.toString();
        }

        // 4.5 阶段2补充: 命中时下载知眸标注图 → 转PNG → 落盘到二期专用目录
        //     结构: {detectRootDir}/{yyyyMMdd}/{taskUuid}/annotated/{baseName}.png
        //     失败只warn不阻塞入库(告警降级显示原图)
        String annotatedRelPath = null;
        if (StrUtil.isNotBlank(zhimouAppImage) && totalHit > 0) {
            try {
                String baseUrl = properties.getOpenapi() != null ? StrUtil.trimToEmpty(properties.getOpenapi().getBaseUrl()) : "";
                if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                String fullUrl = zhimouAppImage.startsWith("/") ? baseUrl + zhimouAppImage
                        : (zhimouAppImage.toLowerCase().startsWith("http") ? zhimouAppImage : baseUrl + "/" + zhimouAppImage);
                byte[] annoBytes = cn.hutool.http.HttpUtil.downloadBytes(fullUrl);
                if (annoBytes != null && annoBytes.length > 0) {
                    byte[] pngBytes = convertToPngBytes(annoBytes);
                    if (pngBytes != null && pngBytes.length > 0) {
                        String dayDir = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                        // taskUuid 优先取 zhimouTaskId; 文件名优先用 localImageName 的 baseName
                        String dirUuid = StrUtil.isNotBlank(zhimouTaskId) ? zhimouTaskId : "unknown";
                        String baseName = (StrUtil.isNotBlank(localImageName) && localImageName.contains("/"))
                                ? localImageName.substring(localImageName.lastIndexOf('/') + 1)
                                : (StrUtil.isNotBlank(localImageName) ? localImageName : "frame_" + frameId);
                        int dotIdx = baseName.lastIndexOf('.');
                        if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);
                        java.nio.file.Path annoDir = java.nio.file.Paths.get(detectRootDir, dayDir, dirUuid, "annotated");
                        java.nio.file.Files.createDirectories(annoDir);
                        java.nio.file.Path annoFile = annoDir.resolve(baseName + ".png");
                        java.nio.file.Files.write(annoFile, pngBytes);
                        annotatedRelPath = dayDir + "/" + dirUuid + "/annotated/" + baseName + ".png";
                        log.info("[知眸-图片回调] 标注图落盘成功 relPath={} size={}", annotatedRelPath, pngBytes.length);
                    } else {
                        log.warn("[知眸-图片回调] 标注图转PNG失败, 告警将降级显示原图 appImage={}", fullUrl);
                    }
                } else {
                    log.warn("[知眸-图片回调] 标注图下载返回空 fullUrl={}", fullUrl);
                }
            } catch (Exception annoEx) {
                log.warn("[知眸-图片回调] 标注图落盘异常(不影响入库) appImage={}, err={}", zhimouAppImage, annoEx.getMessage());
            }
        }

        // 5. 入库 zhimou_image_detection 表
        ZhimouImageDetection rec = new ZhimouImageDetection();
        rec.setZhimouTaskId(zhimouTaskId);
        rec.setBusinessId(businessId);
        rec.setZapkId(zapkId);
        rec.setAppId(appId);
        rec.setAppName(appName);
        rec.setDeviceId(device);
        rec.setLocalImageName(localImageName);
        rec.setZhimouImagePath(zhimouImagePath);
        rec.setZhimouAppImage(zhimouAppImage);
        rec.setAnnotatedLocalPath(annotatedRelPath);
        rec.setLocalImagePath(localImagePath);
        rec.setImageWidth(imageWidth);
        rec.setImageHeight(imageHeight);
        rec.setFrameId(frameIdBox);
        rec.setFrameEnhance(frameEnhance);
        rec.setLatitude(lat);
        rec.setLongitude(lon);
        rec.setAltitude(alt);
        rec.setHitCount(totalHit);
        if (targetsJsonStr != null) rec.setTargetsJson(targetsJsonStr);
        rec.setEventTimestampMs(eventTs);
        rec.setEventTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(eventTs), ZoneId.systemDefault()));
        rec.setRawCallbackJson(rawJson);
        rec.setCallbackEvent(root.path("event").asText(null));
        rec.setCleanStatus(0); // 0=未清洗
        zhimouImageDetectionMapper.insert(rec);
        log.info("[知眸-图片回调] 入库成功 id={}, taskId={}, frameId={}, localImageName={}, hit={}, gps=({},{},{})",
                rec.getId(), zhimouTaskId, frameIdBox, localImageName, totalHit,
                lat != null ? lat.toPlainString() : "N/A",
                lon != null ? lon.toPlainString() : "N/A",
                alt != null ? alt.toPlainString() : "N/A");

        // 6. 阶段3: 写入图片级 Redis 状态 + 自动触发数据清洗 (替换二期任务9 TODO)
        triggerImageCleanupIfReady(localImageName);

        return null; // 图片表是另一个实体, 这里返回 null (与视频表返回类型兼容)
    }

    /**
     * 阶段3新增：知眸回调入库/自有模型入库后统一调用：
     *   写入 minio:flight:img:{localImageName} 当前侧字段(zhimou=1 或 self=1)，
     *   若两侧都到齐 → 立刻调 ImageCleanupService.cleanupSingleImage 自动清洗
     *   → 清洗后尝试标记所属 MinIO 批次完成（按 TASK_UUID 前缀）。
     *
     * 对阶段2手动入口"无害"：若仅一侧写入（如手动自测知眸但自有模型侧是旧分支未写self=1），
     *   本函数仅写 hash 的一个字段，imgStatus 只有一个 field，不会触发清洗。
     *
     * @param localImageName 图片关联键，形式：
     *   阶段2手动入口 = DJI_20260826130711_0001_V
     *   阶段3MinIO入口 = {TASK_UUID}/DJI_..._0001_V  （含 "/"）
     */
    public void triggerImageCleanupIfReady(String localImageName) {
        if (StrUtil.isBlank(localImageName)) return;
        try {
            String imgKey = "minio:flight:img:" + localImageName;
            Map<Object, Object> imgStatus = stringRedisTemplate.opsForHash().entries(imgKey);
            // 只要 clean_status 已经通过手动/其他侧洗过（cleaned=1 标过），直接跳过
            String cleaned = imgStatus.get("cleaned") != null ? imgStatus.get("cleaned").toString() : null;
            if ("1".equals(cleaned)) return;

            // DB 兜底：如果 Redis key 不存在（已被清洗删除），查 DB 判断状态
            if (imgStatus == null || imgStatus.isEmpty()) {
                if (isBothSideCleaned(localImageName)) {
                    log.info("[自动清洗] Redis key不存在但DB已清洗完成，直接标记批次 localImageName={}", localImageName);
                    tryMarkBatchCompleteByPrefix(localImageName);
                    return;
                }
            }

            // 写 zhimou=1
            stringRedisTemplate.opsForHash().put(imgKey, "zhimou", "1");
            stringRedisTemplate.expire(imgKey, 7, TimeUnit.DAYS);

            // 再读一次，确认两侧到齐
            imgStatus = stringRedisTemplate.opsForHash().entries(imgKey);
            boolean zhimouReady = "1".equals(imgStatus.get("zhimou") != null ? imgStatus.get("zhimou").toString() : null);
            boolean selfReady = "1".equals(imgStatus.get("self") != null ? imgStatus.get("self").toString() : null);
            if (zhimouReady && selfReady) {
                log.info("[自动清洗] ⭐ 知眸与自有模型结果均到齐 → 启动 cleanup localImageName={}", localImageName);
                try {
                    imageCleanupService.cleanupSingleImage(localImageName);
                    stringRedisTemplate.opsForHash().put(imgKey, "cleaned", "1");
                    stringRedisTemplate.delete(imgKey);
                } catch (Exception ce) {
                    log.error("[自动清洗] 异常 localImageName={}", localImageName, ce);
                }
                tryMarkBatchCompleteByPrefix(localImageName);
            }
        } catch (Exception e) {
            log.warn("[自动清洗] triggerImageCleanupIfReady 异常 localImageName={}", localImageName, e);
        }
    }

    /**
     * 阶段3新增：自有模型入库后写入 self=1，检查到齐后触发清洗（与知眸侧镜像对称）
     * 放入 Service 层而不是 SelfModelDetectService 直接写，是为了复用 tryMarkBatchCompleteByPrefix。
     */
    public void triggerImageCleanupIfReadyForSelf(String localImageName) {
        if (StrUtil.isBlank(localImageName)) return;
        try {
            String imgKey = "minio:flight:img:" + localImageName;
            Map<Object, Object> imgStatus = stringRedisTemplate.opsForHash().entries(imgKey);

            // DB 兜底：如果 Redis key 不存在（已被清洗删除），查 DB 判断状态
            if (imgStatus == null || imgStatus.isEmpty()) {
                if (isBothSideCleaned(localImageName)) {
                    log.info("[自动清洗] Redis key不存在但DB已清洗完成，直接标记批次 localImageName={}", localImageName);
                    tryMarkBatchCompleteByPrefix(localImageName);
                    return;
                }
            }

            // 写 self=1
            stringRedisTemplate.opsForHash().put(imgKey, "self", "1");
            stringRedisTemplate.expire(imgKey, 7, TimeUnit.DAYS);

            imgStatus = stringRedisTemplate.opsForHash().entries(imgKey);
            String cleaned = imgStatus.get("cleaned") != null ? imgStatus.get("cleaned").toString() : null;
            if ("1".equals(cleaned)) return;
            boolean zhimouReady = "1".equals(imgStatus.get("zhimou") != null ? imgStatus.get("zhimou").toString() : null);
            boolean selfReady = "1".equals(imgStatus.get("self") != null ? imgStatus.get("self").toString() : null);
            if (zhimouReady && selfReady) {
                log.info("[自动清洗] ⭐ 自有模型与知眸结果均到齐 → 启动 cleanup localImageName={}", localImageName);
                try {
                    imageCleanupService.cleanupSingleImage(localImageName);
                    stringRedisTemplate.opsForHash().put(imgKey, "cleaned", "1");
                    stringRedisTemplate.delete(imgKey);
                } catch (Exception ce) {
                    log.error("[自动清洗] 异常 localImageName={}", localImageName, ce);
                }
                tryMarkBatchCompleteByPrefix(localImageName);
            }
        } catch (Exception e) {
            log.warn("[自动清洗] triggerImageCleanupIfReadyForSelf 异常 localImageName={}", localImageName, e);
        }
    }

    /**
     * 检查指定 localImageName 在知眸和自有模型两侧是否都已完成清洗 (clean_status != 0)
     * 用于 Redis key 丢失时的 DB 兜底判断
     */
    private boolean isBothSideCleaned(String localImageName) {
        if (StrUtil.isBlank(localImageName)) return false;
        try {
            long zCount = zhimouImageDetectionMapper.selectCount(
                    new LambdaQueryWrapper<ZhimouImageDetection>()
                            .eq(ZhimouImageDetection::getLocalImageName, localImageName)
                            .eq(ZhimouImageDetection::getDeleted, 0)
                            .ne(ZhimouImageDetection::getCleanStatus, 0));
            long sCount = selfModelImageDetectionMapper.selectCount(
                    new LambdaQueryWrapper<SelfModelImageDetection>()
                            .eq(SelfModelImageDetection::getLocalImageName, localImageName)
                            .eq(SelfModelImageDetection::getDeleted, 0)
                            .ne(SelfModelImageDetection::getCleanStatus, 0));
            return zCount > 0 && sCount > 0;
        } catch (Exception e) {
            log.warn("[自动清洗] isBothSideCleaned 异常 localImageName={}", localImageName, e);
            return false;
        }
    }

    /**
     * 阶段3新增：检查当前 localImageName 所属批次 TASK_UUID 下是否还有未清洗图片；
     * 若本张 imgKey 已 delete，且 keys("minio:flight:img:{TASK_UUID}*") 为空
     *   → minio:flight:{TASK_UUID}=COMPLETED 永久
     *   → 调 ImageCleanupService.tryCompleteMinioBatch 删除 temp 目录
     */
    private void tryMarkBatchCompleteByPrefix(String localImageName) {
        if (StrUtil.isBlank(localImageName)) return;
        int slash = localImageName.indexOf('/');
        if (slash <= 0) return; // 阶段2无前缀，跳过
        String taskUuid = localImageName.substring(0, slash);
        try {
            imageCleanupService.tryCompleteMinioBatch(taskUuid);
        } catch (Exception e) {
            log.warn("[自动清洗] 标记批次完成异常 taskUuid={}", taskUuid, e);
        }
    }

    /** 字符串转 BigDecimal, 失败/空 返回null */
    private BigDecimal parseBigDecimal(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 保存图片到本机, 返回相对路径(含日期子目录), 强制统一使用.png扩展名
     *   例: 20260709/1719990000000_ab3d8f.png
     */
    private String saveImageToLocal(byte[] bytes, String format, long timestampMs) {
        java.nio.file.Path dirPath = null;
        java.nio.file.Path outPath = null;
        try {
            String saveRootDir = properties.getImage().getSaveRootDir();
            String dateDir = DateUtil.format(new Date(timestampMs), "yyyyMMdd");
            // ✅全程用Path/File构造函数拼接(Java自动处理Windows反斜杠), 永远不要手动拼"/"
            dirPath = new File(saveRootDir, dateDir).toPath().toAbsolutePath().normalize();
            // ✅强制造目录(即使已经存在也无副作用) — 解决File.exists()误判但Files.write()找不到的问题
            java.nio.file.Files.createDirectories(dirPath);
            if (!java.nio.file.Files.isDirectory(dirPath)) {
                log.error("[知眸-存图-FAIL] Files.createDirectories后依然不是目录! saveRootDir={} dirPath={} Files.isDir={}",
                        saveRootDir, dirPath, java.nio.file.Files.isDirectory(dirPath));
                return null;
            }
            String ext = "png";
            String fileName = timestampMs + "_" + IdUtil.fastSimpleUUID().substring(0, 8).toLowerCase(Locale.ROOT) + "." + ext;
            outPath = dirPath.resolve(fileName);
            // ✅JDK原生Files.write + 强开CREATE_NEW/TRUNCATE_EXISTING
            java.nio.file.Files.write(outPath, bytes,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);
            String rel = dateDir + "/" + fileName;
            log.info("[知眸-存图-OK] saveRootDir={} dir={} file={} size={} rel={} abs={} FileExistsNow={}",
                    saveRootDir, dirPath, fileName, bytes.length, rel, outPath,
                    java.nio.file.Files.exists(outPath) ? "✅" : "❓");
            return rel;
        } catch (Throwable t) {
            String rootInfo = (properties != null && properties.getImage() != null) ?
                    "saveRootDir=" + properties.getImage().getSaveRootDir() : "";
            String dirInfo = (dirPath != null) ? ("dirPath=" + dirPath + "|Files.exists(dir)=" + java.nio.file.Files.exists(dirPath)) : "";
            String outInfo = (outPath != null) ? ("outPath=" + outPath) : "";
            log.error("[知眸-存图-FAIL] {} | {} | {} | 异常: {} {} → 仍继续保存后续结构化数据和GPS",
                    rootInfo, dirInfo, outInfo, t.getClass().getSimpleName(), t.getMessage(), t);
            return null;
        }
    }

    /**
     * 把任意格式的图片字节数组(JPG/PNG/BMP/WEBP/GIF等) 转成标准PNG字节数组
     * 无法识别(非合法图片)时返回null, 调用方自己决定是否原样保存
     */
    private byte[] convertToPngBytes(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(rawBytes);
             ByteArrayOutputStream bos = new ByteArrayOutputStream(rawBytes.length)) {
            BufferedImage bi = ImageIO.read(bis);
            if (bi == null) {
                log.warn("[知眸-转PNG] ImageIO.read返回null(不是合法图片), 返回null不转码");
                return null;
            }
            // 确保颜色空间兼容(把可能的4通道/CMYK统一转成RGB, 避免浏览器显示异常)
            int w = bi.getWidth();
            int h = bi.getHeight();
            BufferedImage rgbBi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            rgbBi.getGraphics().drawImage(bi, 0, 0, null);
            boolean ok = ImageIO.write(rgbBi, "png", bos);
            if (!ok) {
                log.warn("[知眸-转PNG] ImageIO.write(png)返回false, 返回null");
                return null;
            }
            bos.flush();
            byte[] out = bos.toByteArray();
            log.debug("[知眸-转PNG] 原始字节={} → PNG字节={}", rawBytes.length, out.length);
            return out;
        } catch (Exception e) {
            log.warn("[知眸-转PNG] 异常: {} {} → 回退到不转码(直接保存原始字节但扩展名按调用方逻辑)",
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private BigDecimal toDecimal(String v) {
        if (StrUtil.isBlank(v)) return null;
        try { return new BigDecimal(v); } catch (Exception e) { return null; }
    }

    /**
     * 尝试从 device_realtime_status 表取最近一次飞机GPS作为兜底(知眸回调没带flight数据时)
     */
    private Double[] findLatestGpsFromOsd(String aircraftSn) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.dji.sikong.entity.DeviceRealtimeStatus> qw =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            qw.eq("device_sn", aircraftSn).or().eq("gateway_sn", aircraftSn);
            qw.orderByDesc("id").last("LIMIT 1");
            List<com.dji.sikong.entity.DeviceRealtimeStatus> rs = com.dji.sikong.SikongSpringContextHolder.getBean(
                    com.dji.sikong.mapper.DeviceRealtimeStatusMapper.class
            ).selectList(qw);
            if (!CollectionUtils.isEmpty(rs)) {
                com.dji.sikong.entity.DeviceRealtimeStatus s = rs.get(0);
                if (s.getLatitude() != null && s.getLongitude() != null) {
                    Double lat = s.getLatitude().doubleValue();
                    Double lon = s.getLongitude().doubleValue();
                    Double h = s.getHeight() != null ? s.getHeight().doubleValue() : null;
                    return new Double[]{lat, lon, h};
                }
            }
        } catch (Exception e) {
            log.debug("[知眸-回调] 从OSD缓存取GPS失败", e);
        }
        return null;
    }

    /**
     * MQTT广播报警消息给前端, topic: zhimou/alert/{aircraftSn} 和 zhimou/alert/all
     */
    private void pushAlertViaMqtt(ZhimouDetectionResult dr) {
        try {
            if (djiMqttClient == null) return;
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("id", dr.getId());
            msg.put("zhimouTaskId", dr.getZhimouTaskId());
            msg.put("aircraftSn", dr.getAircraftSn());
            msg.put("ruleId", dr.getRuleId());
            msg.put("ruleName", dr.getRuleName());
            msg.put("hitCount", dr.getHitCount());
            msg.put("eventTime", dr.getEventTime() == null ? null : dr.getEventTime().toString());
            msg.put("eventTimestampMs", dr.getEventTimestampMs());
            msg.put("latitude", dr.getLatitude());
            msg.put("longitude", dr.getLongitude());
            msg.put("height", dr.getHeight());
            msg.put("imageHttpUrl", buildImageHttpUrl(dr.getImageRelativePath()));
            msg.put("imageHasOsd", dr.getImageHasOsd());
            if (StrUtil.isNotBlank(dr.getTargetsJson())) msg.put("targetsSummary", buildTargetsSummary(dr.getTargetsJson()));
            String json = JSONUtil.toJsonStr(msg);
            String topic1 = ZHIMOU_ALERT_TOPIC_PREFIX + (StrUtil.isNotBlank(dr.getAircraftSn()) ? dr.getAircraftSn() : "all");
            djiMqttClient.publish(topic1, json, 1);
            djiMqttClient.publish(ZHIMOU_ALERT_TOPIC_PREFIX + "all", json, 1);
            log.info("[知眸-回调-MQTT] 广播报警topic={}, message={}", topic1, json);
        } catch (Exception e) {
            log.warn("[知眸-回调] MQTT推送前端失败(不影响DB写入)", e);
        }
    }

    private List<Map<String, Object>> buildTargetsSummary(String targetsJson) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            cn.hutool.json.JSONArray arr = JSONUtil.parseArray(targetsJson);
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    cn.hutool.json.JSONObject rule = arr.getJSONObject(i);
                    if (rule == null) continue;
                    Object tgsObj = rule.get("targets");
                    if (tgsObj instanceof cn.hutool.json.JSONArray) {
                        cn.hutool.json.JSONArray tgs = (cn.hutool.json.JSONArray) tgsObj;
                        for (int j = 0; j < tgs.size(); j++) {
                            cn.hutool.json.JSONObject t = tgs.getJSONObject(j);
                            if (t == null) continue;
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("class_name", t.getStr("class_name"));
                            Object conf = t.get("confidence");
                            m.put("confidence", conf == null ? 0 : Double.parseDouble(conf.toString()));
                            m.put("track_id", t.getStr("track_id"));
                            Object box = t.get("box");
                            m.put("box", box == null ? null : box.toString());
                            list.add(m);
                            if (list.size() >= 20) return list;
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return list;
    }

    // ================================================================
    // 前端查询接口
    // ================================================================
    @Override
    public List<ZhimouDetectionResult> listLatestResults(String aircraftSn, int limit) {
        LambdaQueryWrapper<ZhimouDetectionResult> qw = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(aircraftSn)) qw.eq(ZhimouDetectionResult::getAircraftSn, aircraftSn);
        qw.orderByDesc(ZhimouDetectionResult::getEventTimestampMs).last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return detectionResultMapper.selectList(qw);
    }

    @Override
    public Page<ZhimouDetectionResult> pageResults(String aircraftSn, String ruleId, String ruleName, String deviceName, String routeName, String status, String startTime, String endTime, int page, int size) {
        LambdaQueryWrapper<ZhimouDetectionResult> qw = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(aircraftSn)) qw.eq(ZhimouDetectionResult::getAircraftSn, aircraftSn);
        if (StrUtil.isNotBlank(ruleId)) qw.eq(ZhimouDetectionResult::getRuleId, ruleId);
        if (StrUtil.isNotBlank(ruleName)) qw.like(ZhimouDetectionResult::getRuleName, ruleName);
        if ("handled".equals(status)) {
            qw.isNotNull(ZhimouDetectionResult::getWorkOrderId);
        } else if ("unhandled".equals(status)) {
            qw.isNull(ZhimouDetectionResult::getWorkOrderId);
        }
        if (StrUtil.isNotBlank(startTime)) qw.ge(ZhimouDetectionResult::getEventTime, startTime);
        if (StrUtil.isNotBlank(endTime)) qw.le(ZhimouDetectionResult::getEventTime, endTime);
        
        if (StrUtil.isNotBlank(deviceName)) {
            List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>().like(Device::getDeviceName, deviceName));
            if (!devices.isEmpty()) {
                List<String> snList = devices.stream().map(Device::getSn).collect(java.util.stream.Collectors.toList());
                qw.in(ZhimouDetectionResult::getAircraftSn, snList);
            } else {
                qw.eq(ZhimouDetectionResult::getAircraftSn, "");
            }
        }
        
        if (StrUtil.isNotBlank(routeName)) {
            List<Wayline> waylines = waylineMapper.selectList(new LambdaQueryWrapper<Wayline>().like(Wayline::getWaylineName, routeName));
            if (!waylines.isEmpty()) {
                List<String> uuidList = waylines.stream().map(Wayline::getWaylineUuid).collect(java.util.stream.Collectors.toList());
                qw.in(ZhimouDetectionResult::getWaylineUuid, uuidList);
            } else {
                qw.eq(ZhimouDetectionResult::getWaylineUuid, "");
            }
        }
        
        qw.orderByDesc(ZhimouDetectionResult::getEventTimestampMs);
        return detectionResultMapper.selectPage(new Page<>(page, size), qw);
    }

    @Override
    public ZhimouDetectionResult getResultDetail(Long id) {
        return detectionResultMapper.selectById(id);
    }

    @Override
    public boolean restoreResult(Long id) {
        ZhimouDetectionResult result = detectionResultMapper.selectById(id);
        if (result == null) {
            return false;
        }

        if (result.getWorkOrderId() != null) {
            WorkOrder workOrder = workOrderMapper.selectById(result.getWorkOrderId());
            if (workOrder != null) {
                workOrder.setAlarmId(1000000000L + workOrder.getId());
                workOrderMapper.updateById(workOrder);
            }
        }

        LambdaUpdateWrapper<ZhimouDetectionResult> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ZhimouDetectionResult::getId, id).set(ZhimouDetectionResult::getWorkOrderId, null);
        return detectionResultMapper.update(null, wrapper) > 0;
    }

    @Override
    public String buildImageHttpUrl(String imageRelativePath) {
        if (StrUtil.isBlank(imageRelativePath)) return "";
        String prefix = properties.getImage().getUrlPrefix();
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        // context-path 是 /api, 所以前端相对路径是 /api/zhimou-img/20260709/xxx.jpg
        String ctx = "/api";
        return ctx + (prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix) + "/" + imageRelativePath;
    }

    // ================================================================
    // 数据统计: 按 target_id 聚合(targets_json 展开, 去重计数)
    // ================================================================

    /** 构建数据统计查询条件(复用 pageResults 的过滤逻辑, 不含 ruleId/status) */
    private LambdaQueryWrapper<ZhimouDetectionResult> buildStatisticsQuery(
            String aircraftSn, String ruleName, String deviceName, String routeName,
            String startTime, String endTime) {
        LambdaQueryWrapper<ZhimouDetectionResult> qw = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(aircraftSn)) qw.eq(ZhimouDetectionResult::getAircraftSn, aircraftSn);
        if (StrUtil.isNotBlank(ruleName)) qw.like(ZhimouDetectionResult::getRuleName, ruleName);
        if (StrUtil.isNotBlank(startTime)) qw.ge(ZhimouDetectionResult::getEventTime, startTime);
        if (StrUtil.isNotBlank(endTime)) qw.le(ZhimouDetectionResult::getEventTime, endTime);

        if (StrUtil.isNotBlank(deviceName)) {
            List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>().like(Device::getDeviceName, deviceName));
            if (!devices.isEmpty()) {
                List<String> snList = devices.stream().map(Device::getSn).collect(java.util.stream.Collectors.toList());
                qw.in(ZhimouDetectionResult::getAircraftSn, snList);
            } else {
                qw.eq(ZhimouDetectionResult::getAircraftSn, "");
            }
        }

        if (StrUtil.isNotBlank(routeName)) {
            List<Wayline> waylines = waylineMapper.selectList(new LambdaQueryWrapper<Wayline>().like(Wayline::getWaylineName, routeName));
            if (!waylines.isEmpty()) {
                List<String> uuidList = waylines.stream().map(Wayline::getWaylineUuid).collect(java.util.stream.Collectors.toList());
                qw.in(ZhimouDetectionResult::getWaylineUuid, uuidList);
            } else {
                qw.eq(ZhimouDetectionResult::getWaylineUuid, "");
            }
        }

        qw.isNotNull(ZhimouDetectionResult::getTargetsJson);
        qw.orderByDesc(ZhimouDetectionResult::getEventTimestampMs);
        return qw;
    }

    /**
     * 解析 records 的 targets_json, 按 target_id 聚合
     * 每行 = 一个唯一 target_id, 数量 = 被识别次数
     * 关联最近一次识别的 result 记录(取 event_timestamp_ms 最大的那条, 复用其图片)
     */
    private List<Map<String, Object>> aggregateTargets(List<ZhimouDetectionResult> records,
                                                         String targetName, String targetColor, String keyword) {
        Map<String, Map<String, Object>> aggMap = new LinkedHashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (ZhimouDetectionResult dr : records) {
            if (StrUtil.isBlank(dr.getTargetsJson())) continue;
            // 无图片的记录不加入统计
            if (StrUtil.isBlank(dr.getImageRelativePath())) continue;
            try {
                JsonNode rulesNode = mapper.readTree(dr.getTargetsJson());
                if (rulesNode == null || !rulesNode.isArray()) continue;
                for (JsonNode rule : rulesNode) {
                    String ruleNameInJson = rule.path("rule_name").asText("");
                    JsonNode targets = rule.path("targets");
                    if (targets == null || !targets.isArray()) continue;
                    for (JsonNode target : targets) {
                        String targetId = target.path("target_id").asText("");
                        if (StrUtil.isBlank(targetId)) continue;

                        String className = target.path("class").asText("");
                        String color = target.path("color").asText("");
                        double confidence = target.path("confidence").asDouble(0);

                        // targets_json 内部字段过滤
                        if (StrUtil.isNotBlank(targetName) && !targetName.equals(className)) continue;
                        if (StrUtil.isNotBlank(targetColor)) {
                            if ("无".equals(targetColor)) {
                                if (StrUtil.isNotBlank(color)) continue;
                            } else if (!targetColor.equals(color)) continue;
                        }
                        if (StrUtil.isNotBlank(keyword)
                                && !targetId.toLowerCase().contains(keyword.toLowerCase())
                                && !className.toLowerCase().contains(keyword.toLowerCase())) continue;

                        Map<String, Object> agg = aggMap.get(targetId);
                        if (agg == null) {
                            agg = new LinkedHashMap<>();
                            agg.put("target_id", targetId);
                            agg.put("target_name", className);
                            agg.put("target_color", color);
                            agg.put("count", 1);
                            agg.put("max_confidence", confidence);
                            agg.put("first_event_time", dr.getEventTime());
                            agg.put("first_event_timestamp_ms", dr.getEventTimestampMs());
                            agg.put("last_event_time", dr.getEventTime());
                            agg.put("last_event_timestamp_ms", dr.getEventTimestampMs());
                            agg.put("result_id", dr.getId());
                            agg.put("image_relative_path", dr.getImageRelativePath());
                            agg.put("aircraft_sn", dr.getAircraftSn());
                            agg.put("wayline_uuid", dr.getWaylineUuid() != null ? dr.getWaylineUuid() : "");
                            agg.put("rule_name", ruleNameInJson);
                            aggMap.put(targetId, agg);
                        } else {
                            agg.put("count", (int) agg.get("count") + 1);
                            if (confidence > (double) agg.get("max_confidence")) {
                                agg.put("max_confidence", confidence);
                            }
                            Long firstTs = (Long) agg.get("first_event_timestamp_ms");
                            if (dr.getEventTimestampMs() != null && (firstTs == null || dr.getEventTimestampMs() < firstTs)) {
                                agg.put("first_event_time", dr.getEventTime());
                                agg.put("first_event_timestamp_ms", dr.getEventTimestampMs());
                            }
                            Long lastTs = (Long) agg.get("last_event_timestamp_ms");
                            if (dr.getEventTimestampMs() != null && (lastTs == null || dr.getEventTimestampMs() > lastTs)) {
                                agg.put("last_event_time", dr.getEventTime());
                                agg.put("last_event_timestamp_ms", dr.getEventTimestampMs());
                                agg.put("result_id", dr.getId());
                                agg.put("image_relative_path", dr.getImageRelativePath());
                                agg.put("aircraft_sn", dr.getAircraftSn());
                                agg.put("wayline_uuid", dr.getWaylineUuid() != null ? dr.getWaylineUuid() : "");
                                agg.put("rule_name", ruleNameInJson);
                                agg.put("target_name", className);
                                agg.put("target_color", color);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[数据统计]解析targets_json失败 id={}", dr.getId(), e);
            }
        }

        List<Map<String, Object>> list = new ArrayList<>(aggMap.values());
        list.sort((a, b) -> {
            Long ta = (Long) a.get("last_event_timestamp_ms");
            Long tb = (Long) b.get("last_event_timestamp_ms");
            long va = ta != null ? ta : 0L;
            long vb = tb != null ? tb : 0L;
            return Long.compare(vb, va);
        });
        return list;
    }

    @Override
    public Map<String, Object> statisticsByTarget(String aircraftSn, String ruleName, String deviceName, String routeName,
                                                   String targetName, String targetColor, String keyword,
                                                   String startTime, String endTime, int page, int size) {
        LambdaQueryWrapper<ZhimouDetectionResult> qw = buildStatisticsQuery(aircraftSn, ruleName, deviceName, routeName, startTime, endTime);
        qw.last("LIMIT 5000");
        List<ZhimouDetectionResult> records = detectionResultMapper.selectList(qw);

        List<Map<String, Object>> all = aggregateTargets(records, targetName, targetColor, keyword);

        int total = all.size();
        int from = Math.min(Math.max(0, (page - 1) * size), total);
        int to = Math.min(from + size, total);
        List<Map<String, Object>> pageList = new ArrayList<>(all.subList(from, to));

        for (Map<String, Object> m : pageList) {
            m.put("image_http_url", buildImageHttpUrl((String) m.get("image_relative_path")));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", pageList);
        res.put("total", total);
        res.put("total_count", all.stream().mapToInt(m -> (int) m.get("count")).sum());
        res.put("page", page);
        res.put("size", size);
        return res;
    }

    @Override
    public List<Map<String, Object>> statisticsExport(String aircraftSn, String ruleName, String deviceName, String routeName,
                                                      String targetName, String targetColor, String keyword,
                                                      String startTime, String endTime, int maxSize) {
        LambdaQueryWrapper<ZhimouDetectionResult> qw = buildStatisticsQuery(aircraftSn, ruleName, deviceName, routeName, startTime, endTime);
        qw.last("LIMIT " + Math.max(1, Math.min(maxSize, 10000)));
        List<ZhimouDetectionResult> records = detectionResultMapper.selectList(qw);

        List<Map<String, Object>> all = aggregateTargets(records, targetName, targetColor, keyword);
        for (Map<String, Object> m : all) {
            m.put("image_http_url", buildImageHttpUrl((String) m.get("image_relative_path")));
        }
        return all;
    }

    @Override
    public List<String> listDistinctTargetNames() {
        LambdaQueryWrapper<ZhimouDetectionResult> qw = new LambdaQueryWrapper<>();
        qw.isNotNull(ZhimouDetectionResult::getTargetsJson);
        qw.isNotNull(ZhimouDetectionResult::getImageRelativePath);
        qw.ne(ZhimouDetectionResult::getImageRelativePath, "");
        qw.orderByDesc(ZhimouDetectionResult::getEventTimestampMs);
        qw.last("LIMIT 5000");
        List<ZhimouDetectionResult> records = detectionResultMapper.selectList(qw);

        Set<String> names = new TreeSet<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (ZhimouDetectionResult dr : records) {
            if (StrUtil.isBlank(dr.getTargetsJson())) continue;
            try {
                JsonNode rulesNode = mapper.readTree(dr.getTargetsJson());
                if (rulesNode == null || !rulesNode.isArray()) continue;
                for (JsonNode rule : rulesNode) {
                    JsonNode targets = rule.path("targets");
                    if (targets == null || !targets.isArray()) continue;
                    for (JsonNode target : targets) {
                        String className = target.path("class").asText("");
                        if (StrUtil.isNotBlank(className)) names.add(className);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(names);
    }

    @Override
    public List<String> listDistinctTargetColors() {
        LambdaQueryWrapper<ZhimouDetectionResult> qw = new LambdaQueryWrapper<>();
        qw.isNotNull(ZhimouDetectionResult::getTargetsJson);
        qw.isNotNull(ZhimouDetectionResult::getImageRelativePath);
        qw.ne(ZhimouDetectionResult::getImageRelativePath, "");
        qw.orderByDesc(ZhimouDetectionResult::getEventTimestampMs);
        qw.last("LIMIT 5000");
        List<ZhimouDetectionResult> records = detectionResultMapper.selectList(qw);

        Set<String> colors = new TreeSet<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (ZhimouDetectionResult dr : records) {
            if (StrUtil.isBlank(dr.getTargetsJson())) continue;
            try {
                JsonNode rulesNode = mapper.readTree(dr.getTargetsJson());
                if (rulesNode == null || !rulesNode.isArray()) continue;
                for (JsonNode rule : rulesNode) {
                    JsonNode targets = rule.path("targets");
                    if (targets == null || !targets.isArray()) continue;
                    for (JsonNode target : targets) {
                        String color = target.path("color").asText("");
                        if (StrUtil.isNotBlank(color)) colors.add(color);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(colors);
    }

    // ================================================================
    // 图片识别任务预留
    // ================================================================
    @Override
    public String createImageTask(String aircraftSn, String appId, String applicationPackageId) {
        if (StrUtil.isBlank(appId)) {
            log.warn("[知眸-创建图片任务] appId为空");
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", "image");
        // 错误1修复: app_id → app_ids 数组 (支持多应用ID, 逗号分隔)
        body.put("app_ids", Arrays.asList(appId.split(",")));
        // 错误2修复: application_package_id → zapk_id
        if (StrUtil.isNotBlank(applicationPackageId)) body.put("zapk_id", applicationPackageId);
        // 错误3修复: device → device_id
        body.put("device_id", aircraftSn);
        // 错误4修复: 嵌套 report.httpurl → 顶层 report_url
        body.put("report_url", properties.getOpenapi().getCallbackUrl());
        JsonNode resp = zhimouOpenApiClient.createTask(body);
        if (resp == null) return null;
        String taskId = resp.path("task_id").asText(null);
        if (StrUtil.isBlank(taskId)) taskId = resp.path("id").asText(null);
        if (StrUtil.isNotBlank(taskId)) {
            ZhimouTask t = new ZhimouTask();
            t.setZhimouTaskId(taskId);
            t.setAircraftSn(aircraftSn);
            t.setAppId(appId);
            t.setApplicationPackageId(applicationPackageId);
            t.setMode("image");
            t.setReportUrl(properties.getOpenapi().getCallbackUrl());
            t.setStatus(1);
            t.setStartTime(LocalDateTime.now());
            t.setRequestJson(JSONUtil.toJsonStr(body));
            t.setResponseJson(JSONUtil.toJsonStr(resp));
            this.save(t);
        }
        return taskId;
    }

    @Override
    public JsonNode reportImage(String zhimouTaskId, long imageId, String aircraftSn, byte[] imageBytes) {
        return zhimouOpenApiClient.reportImage(zhimouTaskId, imageId, aircraftSn, imageBytes);
    }

    // ================================================================
    // 图片识别任务(长期模式): 一次创建 + 批量上报 + 后续手动关闭
    // ================================================================
    @Override
    public String createAndReportImageBatch(List<File> imageFiles,
                                             String aircraftSn,
                                             String appId,
                                             String applicationPackageId,
                                             String imageBaseDir) {
        // 阶段2旧签名：prefix=null + 1000ms间隔，完全保持原行为
        return createAndReportImageBatch(imageFiles, aircraftSn, appId, applicationPackageId,
                imageBaseDir, null, 1000L);
    }

    @Override
    public String createAndReportImageBatch(List<File> imageFiles,
                                             String aircraftSn,
                                             String appId,
                                             String applicationPackageId,
                                             String imageBaseDir,
                                             String customLocalImagePrefix,
                                             long reportIntervalMs) {
        if (imageFiles == null || imageFiles.isEmpty()) {
            log.warn("[知眸-图片批量上报] 图片列表为空");
            return null;
        }
        // 1. 创建一个长期图片任务
        String taskId = createImageTask(aircraftSn, appId, applicationPackageId);
        if (StrUtil.isBlank(taskId)) {
            log.error("[知眸-图片批量上报] 创建任务失败, 无法继续");
            return null;
        }
        log.info("[知眸-图片批量上报] 创建任务成功 taskId={}, 待上报图片数={}, prefix={}",
                taskId, imageFiles.size(), customLocalImagePrefix);

        long imageId = 1L;
        int success = 0, fail = 0;
        long interval = reportIntervalMs > 0 ? reportIntervalMs : 1000L;

        for (File srcFile : imageFiles) {
            try {
                // 2.1 落盘到 imageBaseDir/yyyyMMdd/{原文件名}
                String dayDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                Path targetDir = Paths.get(imageBaseDir, dayDir);
                Files.createDirectories(targetDir);
                Path targetPath = targetDir.resolve(srcFile.getName());
                if (!targetPath.toFile().exists()) {
                    Files.copy(srcFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                // 2.2 构造 local_image_name
                //   - 阶段2手动入口 (customLocalImagePrefix==null): 沿用旧逻辑, 文件去扩展名
                //   - 阶段3MinIO入口 (customLocalImagePrefix=taskUuid): 加前缀, 解决跨架次同名冲突
                String fileName = srcFile.getName();
                int dotIdx = fileName.lastIndexOf('.');
                String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
                String localImageName = StrUtil.isNotBlank(customLocalImagePrefix)
                        ? customLocalImagePrefix + "/" + baseName
                        : baseName;

                // 2.3 提取 EXIF GPS
                BigDecimal[] gps = ImageExifUtil.extractGps(srcFile);
                BigDecimal lat = gps[0], lon = gps[1], alt = gps[2];

                // 2.4 元数据存 Redis Hash, handleCallback 时用 (taskId, frame.id) 反查
                String metaKey = "zhimou:img:meta:" + taskId + ":" + imageId;
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("local_image_name", localImageName);
                meta.put("local_image_path", dayDir + "/" + fileName);
                meta.put("latitude", lat != null ? lat.toPlainString() : "");
                meta.put("longitude", lon != null ? lon.toPlainString() : "");
                meta.put("altitude", alt != null ? alt.toPlainString() : "");
                stringRedisTemplate.opsForHash().putAll(metaKey, meta);
                stringRedisTemplate.expire(metaKey, 24, TimeUnit.HOURS);

                // 2.5 上报图片 (image_id 会在回调里原样返回为 data.frame.id)
                byte[] imageBytes = Files.readAllBytes(srcFile.toPath());
                JsonNode r = reportImage(taskId, imageId, aircraftSn, imageBytes);
                if (r != null) {
                    success++;
                    log.info("[知眸-图片批量上报] 上报成功 taskId={}, imageId={}, fileName={}, localImageName={}",
                            taskId, imageId, fileName, localImageName);
                } else {
                    fail++;
                    log.warn("[知眸-图片批量上报] 上报失败(超时或API错误) taskId={}, imageId={}, fileName={}, localImageName={}",
                            taskId, imageId, fileName, localImageName);
                    // 2.5.1 失败兜底：创建 DB 记录 + 触发清洗检查，防止批次卡死
                    try {
                        ZhimouImageDetection failRec = new ZhimouImageDetection();
                        failRec.setZhimouTaskId(taskId);
                        failRec.setDeviceId(aircraftSn);
                        failRec.setLocalImageName(localImageName);
                        failRec.setLocalImagePath(dayDir + "/" + fileName);
                        failRec.setImageWidth(0);
                        failRec.setImageHeight(0);
                        failRec.setFrameId(imageId);
                        failRec.setLatitude(lat);
                        failRec.setLongitude(lon);
                        failRec.setAltitude(alt);
                        failRec.setHitCount(0);
                        failRec.setCleanStatus(1); // 1=已处理(失败)
                        failRec.setCleanRemark("知眸上报失败(超时或API错误)");
                        failRec.setCreateTime(LocalDateTime.now());
                        zhimouImageDetectionMapper.insert(failRec);
                        log.info("[知眸-图片批量上报] 失败兜底入库 taskId={}, localImageName={}", taskId, localImageName);

                        // 2.5.2 设置 Redis 图片级状态
                        String imgKey = "minio:flight:img:" + localImageName;
                        Map<String, String> failMeta = new LinkedHashMap<>();
                        failMeta.put("zhimou", "1");
                        failMeta.put("failed", "1");
                        stringRedisTemplate.opsForHash().putAll(imgKey, failMeta);
                        stringRedisTemplate.expire(imgKey, 7, TimeUnit.DAYS);
                        log.info("[知眸-图片批量上报] 失败兜底设置Redis key localImageName={}", localImageName);

                        // 2.5.3 触发批次完成检查
                        triggerImageCleanupIfReady(localImageName);
                        log.info("[知眸-图片批量上报] 失败兜底触发清洗检查 localImageName={}", localImageName);
                    } catch (Exception ex) {
                        log.error("[知眸-图片批量上报] 失败兜底异常 localImageName={}", localImageName, ex);
                    }
                }
            } catch (Exception e) {
                fail++;
                log.error("[知眸-图片批量上报] 异常 taskId={}, imageId={}, file={}",
                        taskId, imageId, srcFile.getName(), e);
            }
            imageId++;
            if (interval > 0) {
                try { Thread.sleep(interval); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        log.info("[知眸-图片批量上报] 完成 taskId={}, 总数={}, 成功={}, 失败={}",
                taskId, imageFiles.size(), success, fail);
        // 主动关闭知眸任务，释放并发名额（知眸并发仅2个）
        try {
            JsonNode stopResp = deleteTask(taskId);
            log.info("[知眸-图片批量上报] 已主动关闭知眸任务 taskId={}, resp={}", taskId, stopResp);
        } catch (Exception e) {
            log.warn("[知眸-图片批量上报] 关闭知眸任务异常 taskId={}", taskId, e);
        }
        return taskId;
    }

    @Override
    public List<LiveStream> listActiveLiveStreamsNeedZhimou() {
        LambdaQueryWrapper<LiveStream> qw = new LambdaQueryWrapper<>();
        qw.eq(LiveStream::getDeleted, 0);
        qw.in(LiveStream::getStatus, 0, 1);
        return liveStreamMapper.selectList(qw);
    }
}
