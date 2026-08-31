package com.dji.sikong.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sikong.common.R;
import com.dji.sikong.dto.StartLiveStreamDTO;
import com.dji.sikong.entity.LiveStream;
import com.dji.sikong.entity.ZhimouDetectionResult;
import com.dji.sikong.service.LiveStreamService;
import com.dji.sikong.service.ZhimouTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.*;

/**
 * 知眸AI识别平台 API
 *  1. 给前端用: 应用包/应用下拉、手动创建/关闭AI任务、报警列表/详情
 *  2. 给知眸回调用: POST /api/zhimou/callback  (知眸会调这个把识别结果塞给我们)
 */
@Slf4j
@RestController
@RequestMapping("/zhimou")
public class ZhimouController {

    /** 二期新增: 数据源开关服务 */
    @javax.annotation.Resource
    private com.dji.sikong.service.TaskSwitchService taskSwitchService;

    /** 二期新增: 知眸图片识别 Mapper */
    @javax.annotation.Resource
    private com.dji.sikong.mapper.ZhimouImageDetectionMapper zhimouImageDetectionMapper;

    @Resource
    private ZhimouTaskService zhimouTaskService;

    @Resource
    private LiveStreamService liveStreamService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // =================================================================
    // 一、给前端用: 查询应用包/应用下拉
    // =================================================================

    /**
     * 查询知眸的应用包列表(进入AI配置页先调这个).
     * 注意: 知眸私有化部署可能未开放此OpenAPI接口, 此时返回明确提示, 需要用户从Web控制台手动获取应用ID.
     */
    @GetMapping("/app-packages")
    public R<?> listAppPackages() {
        JsonNode data = zhimouTaskService.listApplicationPackages();
        if (data == null) {
            Map<String, Object> hint = new LinkedHashMap<>();
            hint.put("hint", "当前知眸私有化部署版本未开放『应用包列表』OpenAPI接口");
            hint.put("solution", "请从知眸Web控制台(低空视觉AI开放平台)手动获取应用包ID和应用ID, 填入后端配置文件 application.yml");
            hint.put("config_keys", new String[]{
                "zhimou.openapi.default-pkg-id: 应用包ID(如 pkg-xxxxxx)",
                "zhimou.openapi.default-app-id: 应用ID(如 app-xxxxxx)"
            });
            hint.put("how_to_get", new String[]{
                "步骤1: 浏览器打开知眸Web控制台(低空视觉AI开放平台)",
                "步骤2: 进入 应用管理 / 算法管理 页面",
                "步骤3: 点击目标应用进入详情页, 从页面URL或详情中复制 应用ID(app_id) 和 应用包ID(pkg_id)",
                "步骤4: 将获取到的ID填入后端 application.yml 对应字段, 重启SpringBoot服务即可生效"
            });
            String configuredAppId = zhimouTaskService.getDefaultAppId();
            String configuredPkgId = zhimouTaskService.getDefaultPkgId();
            if (StrUtil.isNotBlank(configuredAppId) || StrUtil.isNotBlank(configuredPkgId)) {
                Map<String, String> configured = new LinkedHashMap<>();
                configured.put("default_app_id", StrUtil.isNotBlank(configuredAppId) ? configuredAppId : "(未配置)");
                configured.put("default_pkg_id", StrUtil.isNotBlank(configuredPkgId) ? configuredPkgId : "(未配置)");
                hint.put("current_config", configured);
            }
            return new R<>(404, "知眸应用包列表接口不可用,请从Web控制台手动获取应用ID配置", (Object) hint);
        }
        return R.ok(data);
    }

    /**
     * 根据应用包ID查里面的应用/规则列表(前端选完应用包后调, 拿到app_id才能创建任务).
     * 注意: 知眸私有化部署可能未开放此OpenAPI接口, 此时返回明确提示, 需要用户从Web控制台手动获取应用ID.
     */
    @GetMapping("/apps")
    public R<?> listApps(@RequestParam(value = "application_package_id", required = false) String applicationPackageId) {
        JsonNode data = zhimouTaskService.listAppsByPackage(applicationPackageId);
        if (data == null) {
            Map<String, Object> hint = new LinkedHashMap<>();
            hint.put("hint", "当前知眸私有化部署版本未开放『应用列表』OpenAPI接口");
            hint.put("solution", "请从知眸Web控制台(低空视觉AI开放平台)手动获取应用ID(app_id), 填入后端配置文件 application.yml 的 zhimou.openapi.default-app-id 字段");
            hint.put("how_to_get", new String[]{
                "浏览器打开知眸Web控制台 -> 应用管理 -> 进入目标应用详情 -> 复制应用ID(app_id)"
            });
            String configuredAppId = zhimouTaskService.getDefaultAppId();
            if (StrUtil.isNotBlank(configuredAppId)) {
                Map<String, String> cfg = new LinkedHashMap<>();
                cfg.put("default_app_id", configuredAppId);
                hint.put("current_config", cfg);
            }
            return new R<>(404, "知眸应用列表接口不可用,请从Web控制台手动获取应用ID配置", (Object) hint);
        }
        return R.ok(data);
    }

    // =================================================================
    // 二、给前端用: 手动创建/关闭AI任务(正常流程是起飞时自动创建, 这里兜底手动操作)
    // =================================================================

    /**
     * 手动为指定直播创建知眸视频分析任务
     */
    @PostMapping("/task")
    public R<?> createVideoTask(@RequestBody Map<String, Object> params) {
        Long liveStreamId = params.get("live_stream_id") != null ? Long.valueOf(String.valueOf(params.get("live_stream_id"))) : null;
        if (liveStreamId == null) return R.fail("live_stream_id必填");
        LiveStream ls = liveStreamService.getById(liveStreamId);
        if (ls == null) return R.fail("直播记录不存在");
        // app_id / pkg_id 查找优先级:
        //  1) POST参数: app_ids (数组/逗号分隔) 或 app_id (单值)
        //  2) live_stream.zhimou_app_id/pkg_id
        //  3) yml默认配置
        // ✅支持多应用: 接受 app_ids(数组"["id1","id2"]" 或 "id1,id2"字符串) 或 兼容单数 app_id
        java.util.List<String> appIdList = new java.util.ArrayList<>();
        Object rawAppIds = params.get("app_ids");
        if (rawAppIds == null) rawAppIds = params.get("appId");
        if (rawAppIds == null) rawAppIds = params.get("app_id");
        if (rawAppIds != null) {
            if (rawAppIds instanceof java.util.Collection) {
                for (Object item : (java.util.Collection<?>) rawAppIds) {
                    if (item != null) {
                        String s = String.valueOf(item).trim();
                        if (cn.hutool.core.util.StrUtil.isNotBlank(s) && !appIdList.contains(s)) appIdList.add(s);
                    }
                }
            } else {
                String s = String.valueOf(rawAppIds).trim();
                appIdList.addAll(com.dji.sikong.config.ZhimouProperties.parseAppIds(s));
            }
        }
        // 2) 没有参数: 从live_stream取(也支持逗号分隔多ID)
        if (appIdList.isEmpty() && cn.hutool.core.util.StrUtil.isNotBlank(ls.getZhimouAppId())) {
            appIdList.addAll(com.dji.sikong.config.ZhimouProperties.parseAppIds(ls.getZhimouAppId()));
        }
        // 3) 还是空: 从yml默认配置取(同样支持多ID逗号分隔)
        if (appIdList.isEmpty() && cn.hutool.core.util.StrUtil.isNotBlank(zhimouTaskService.getDefaultAppId())) {
            appIdList.addAll(com.dji.sikong.config.ZhimouProperties.parseAppIds(zhimouTaskService.getDefaultAppId()));
        }
        String pkgId = (String) params.get("application_package_id");
        if (cn.hutool.core.util.StrUtil.isBlank(pkgId) && cn.hutool.core.util.StrUtil.isNotBlank(ls.getZhimouPkgId())) pkgId = ls.getZhimouPkgId();
        if (cn.hutool.core.util.StrUtil.isBlank(pkgId) && cn.hutool.core.util.StrUtil.isNotBlank(zhimouTaskService.getDefaultPkgId())) pkgId = zhimouTaskService.getDefaultPkgId();
        if (appIdList.isEmpty()) {
            return R.fail("app_id必填, 请在POST体传app_ids(数组或逗号分隔)/app_id, 或在live_stream表zhimou_app_id字段写入, 或在application.yml配置zhimou.openapi.default-app-id");
        }
        // 转成逗号分隔字符串, 传给下层实现(内部会再解析回List)
        String appIdJoined = String.join(",", appIdList);
        String taskId = zhimouTaskService.createVideoTaskWhenLiveStart(ls, appIdJoined, pkgId);
        Map<String, Object> res = new HashMap<>();
        res.put("zhimou_task_id", taskId);
        res.put("app_ids_used", appIdList);
        if (cn.hutool.core.util.StrUtil.isBlank(taskId)) {
            LiveStream latest = liveStreamService.getById(liveStreamId);
            String reason = "未知原因";
            if (latest != null) {
                Integer zs = latest.getZhimouStatus();
                if (zs != null && zs.intValue() == 1 && cn.hutool.core.util.StrUtil.isNotBlank(latest.getZhimouTaskId())) {
                    reason = "已有运行中的知眸任务(task_id=" + latest.getZhimouTaskId() + "), 未重复创建";
                    res.put("zhimou_task_id", latest.getZhimouTaskId());
                    return R.ok(res);
                }
                if (zs != null && zs.intValue() == 2) {
                    reason = "该直播在2分钟内刚刚主动关闭过知眸任务, 冷却期内不重新创建(防止OSD抖动造成死循环). 如需强制创建, 请等待冷却期结束或清Redis zhimou:close:cool:*锁";
                }
                if (zs != null && zs.intValue() == 3) {
                    reason = "上一次创建任务失败: " + (cn.hutool.core.util.StrUtil.isNotBlank(latest.getZhimouLastError()) ? latest.getZhimouLastError() : "无详情");
                }
            }
            res.put("skipped_reason", reason);
            res.put("skipped", true);
            return new R<>(202, reason, res);
        }
        return R.ok(res);
    }

    /**
     * 手动关闭知眸任务(按飞机SN)
     */
    @DeleteMapping("/task/aircraft/{aircraftSn}")
    public R<?> closeByAircraft(@PathVariable String aircraftSn) {
        boolean ok = zhimouTaskService.closeZhimouTaskByAircraftSn(aircraftSn);
        return ok ? R.ok() : R.fail("关闭失败,请查看日志");
    }

    /**
     * 手动关闭知眸任务(按直播ID)
     */
    @DeleteMapping("/task/live/{liveStreamId}")
    public R<?> closeByLiveStream(@PathVariable Long liveStreamId) {
        boolean ok = zhimouTaskService.closeZhimouTaskByLiveStreamId(liveStreamId);
        return ok ? R.ok() : R.fail("关闭失败,请查看日志");
    }

    /**
     * 查询知眸服务器上的所有任务列表
     */
    @GetMapping("/tasks")
    public R<?> listZhimouTasks() {
        JsonNode data = zhimouTaskService.listTasks();
        if (data == null) {
            return R.fail("获取任务列表失败");
        }
        return R.ok(data);
    }

    /**
     * 强制关闭知眸服务器上的所有任务(清理并发资源)
     */
    @DeleteMapping("/tasks/all")
    public R<?> closeAllZhimouTasks() {
        JsonNode tasks = zhimouTaskService.listTasks();
        if (tasks == null || !tasks.isArray()) {
            return R.fail("获取任务列表失败");
        }
        int closed = 0;
        int failed = 0;
        List<String> taskIds = new ArrayList<>();
        for (JsonNode task : tasks) {
            String taskId = task.path("task_id").asText("");
            if (StrUtil.isNotBlank(taskId)) {
                taskIds.add(taskId);
                JsonNode result = zhimouTaskService.deleteTask(taskId);
                if (result != null) {
                    closed++;
                } else {
                    failed++;
                }
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", taskIds.size());
        res.put("closed", closed);
        res.put("failed", failed);
        res.put("task_ids", taskIds);
        return R.ok(res);
    }

    // =================================================================
    // 三、给前端用: 报警识别结果查询
    // ⭐ 二期改造: 根据全局开关 alert_data_source 切换数据源
    //    video  → 查 zhimou_detection_result 表(一期视频识别)
    //    image  → 查 zhimou_image_detection 表(二期图片识别, 只返回已清洗 clean_status != 0)
    // =================================================================

    /**
     * 前端进入页面拉最近N条报警结果(首页/报警页初始数据)
     */
    @GetMapping("/results/latest")
    public R<List<Map<String, Object>>> latestResults(
            @RequestParam(value = "aircraft_sn", required = false) String aircraftSn,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        String ds = taskSwitchService.getAlertDataSource();
        if ("image".equals(ds)) {
            return R.ok(latestImageResults(aircraftSn, limit));
        }
        List<ZhimouDetectionResult> list = zhimouTaskService.listLatestResults(aircraftSn, limit);
        List<Map<String, Object>> vo = new ArrayList<>();
        for (ZhimouDetectionResult dr : list) {
            vo.add(buildResultVO(dr));
        }
        return R.ok(vo);
    }

    /** 二期新增: 图片识别数据源(只返回已清洗的) */
    private List<Map<String, Object>> latestImageResults(String aircraftSn, int limit) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dji.sikong.entity.ZhimouImageDetection> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        qw.ne(com.dji.sikong.entity.ZhimouImageDetection::getCleanStatus, 0);
        if (StrUtil.isNotBlank(aircraftSn)) {
            qw.eq(com.dji.sikong.entity.ZhimouImageDetection::getDeviceId, aircraftSn);
        }
        qw.orderByDesc(com.dji.sikong.entity.ZhimouImageDetection::getEventTime);
        qw.last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        List<com.dji.sikong.entity.ZhimouImageDetection> list = zhimouImageDetectionMapper.selectList(qw);
        List<Map<String, Object>> vo = new ArrayList<>();
        for (com.dji.sikong.entity.ZhimouImageDetection img : list) {
            vo.add(buildImageResultVO(img));
        }
        return vo;
    }

    /**
     * 分页查询报警结果
     */
    @GetMapping("/results/page")
    public R<Map<String, Object>> pageResults(
            @RequestParam(value = "aircraft_sn", required = false) String aircraftSn,
            @RequestParam(value = "rule_id", required = false) String ruleId,
            @RequestParam(value = "rule_name", required = false) String ruleName,
            @RequestParam(value = "device_name", required = false) String deviceName,
            @RequestParam(value = "route_name", required = false) String routeName,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        // 阶段2补充: 告警管理页固定显示一期视频识别告警(与照片告警页解耦);
        // alert_data_source 开关降级为"大屏警情数据源"专用, 不再影响本接口。
        Page<ZhimouDetectionResult> resultPage = zhimouTaskService.pageResults(aircraftSn, ruleId, ruleName, deviceName, routeName, status, startTime, endTime, page, size);
        List<Map<String, Object>> vo = new ArrayList<>();
        for (ZhimouDetectionResult dr : resultPage.getRecords()) {
            vo.add(buildResultVO(dr));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", vo);
        res.put("total", resultPage.getTotal());
        res.put("page", resultPage.getCurrent());
        res.put("size", resultPage.getSize());
        return R.ok(res);
    }

    /** 二期补充: 照片告警分页(固定查二期表, 只显示已清洗+命中的记录) */
    @GetMapping("/image-results/page")
    public R<Map<String, Object>> pageImageAlarmResults(
            @RequestParam(value = "aircraft_sn", required = false) String aircraftSn,
            @RequestParam(value = "rule_name", required = false) String ruleName,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dji.sikong.entity.ZhimouImageDetection> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        qw.ne(com.dji.sikong.entity.ZhimouImageDetection::getCleanStatus, 0);
        qw.gt(com.dji.sikong.entity.ZhimouImageDetection::getHitCount, 0);
        if (StrUtil.isNotBlank(aircraftSn)) {
            qw.eq(com.dji.sikong.entity.ZhimouImageDetection::getDeviceId, aircraftSn);
        }
        if (StrUtil.isNotBlank(ruleName)) {
            qw.like(com.dji.sikong.entity.ZhimouImageDetection::getAppName, ruleName);
        }
        if ("handled".equalsIgnoreCase(status)) {
            qw.isNotNull(com.dji.sikong.entity.ZhimouImageDetection::getWorkOrderId);
        } else if ("unhandled".equalsIgnoreCase(status)) {
            qw.isNull(com.dji.sikong.entity.ZhimouImageDetection::getWorkOrderId);
        }
        if (StrUtil.isNotBlank(startTime)) {
            qw.ge(com.dji.sikong.entity.ZhimouImageDetection::getEventTime, startTime);
        }
        if (StrUtil.isNotBlank(endTime)) {
            qw.le(com.dji.sikong.entity.ZhimouImageDetection::getEventTime, endTime);
        }
        qw.orderByDesc(com.dji.sikong.entity.ZhimouImageDetection::getEventTime);
        Page<com.dji.sikong.entity.ZhimouImageDetection> p = new Page<>(page, size);
        Page<com.dji.sikong.entity.ZhimouImageDetection> resultPage = zhimouImageDetectionMapper.selectPage(p, qw);
        List<Map<String, Object>> vo = new ArrayList<>();
        for (com.dji.sikong.entity.ZhimouImageDetection img : resultPage.getRecords()) {
            vo.add(buildImageResultVO(img));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", vo);
        res.put("total", resultPage.getTotal());
        res.put("page", resultPage.getCurrent());
        res.put("size", resultPage.getSize());
        return R.ok(res);
    }

    /** 二期补充: 标记照片告警已处理(写work_order_id, 与一期语义一致) */
    @PutMapping("/image-results/{id}/handle")
    public R<Void> handleImageAlarm(@PathVariable("id") Long id) {
        com.dji.sikong.entity.ZhimouImageDetection rec = zhimouImageDetectionMapper.selectById(id);
        if (rec == null) {
            return R.fail("记录不存在: " + id);
        }
        if (rec.getWorkOrderId() == null) {
            rec.setWorkOrderId(System.currentTimeMillis());
            zhimouImageDetectionMapper.updateById(rec);
        }
        return R.ok(null);
    }

    /** 二期新增: 图片识别分页查询(只返回已清洗的) */
    private Map<String, Object> pageImageResults(String aircraftSn, int page, int size) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dji.sikong.entity.ZhimouImageDetection> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        qw.ne(com.dji.sikong.entity.ZhimouImageDetection::getCleanStatus, 0);
        if (StrUtil.isNotBlank(aircraftSn)) {
            qw.eq(com.dji.sikong.entity.ZhimouImageDetection::getDeviceId, aircraftSn);
        }
        qw.orderByDesc(com.dji.sikong.entity.ZhimouImageDetection::getEventTime);
        Page<com.dji.sikong.entity.ZhimouImageDetection> p = new Page<>(page, size);
        Page<com.dji.sikong.entity.ZhimouImageDetection> resultPage = zhimouImageDetectionMapper.selectPage(p, qw);
        List<Map<String, Object>> vo = new ArrayList<>();
        for (com.dji.sikong.entity.ZhimouImageDetection img : resultPage.getRecords()) {
            vo.add(buildImageResultVO(img));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", vo);
        res.put("total", resultPage.getTotal());
        res.put("page", resultPage.getCurrent());
        res.put("size", resultPage.getSize());
        return res;
    }

    /** 二期新增: 图片识别 VO (字段统一, 前端无感知) */
    private Map<String, Object> buildImageResultVO(com.dji.sikong.entity.ZhimouImageDetection img) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", img.getId());
        m.put("zhimou_task_id", img.getZhimouTaskId());
        m.put("aircraft_sn", img.getDeviceId() != null ? img.getDeviceId() : "");
        m.put("rule_id", img.getAppId() != null ? img.getAppId() : "");
        m.put("rule_name", img.getAppName() != null ? img.getAppName() : "");
        m.put("hit_count", img.getHitCount() != null ? img.getHitCount() : 0);
        m.put("event_time", img.getEventTime());
        m.put("event_timestamp_ms", img.getEventTimestampMs());
        m.put("latitude", img.getLatitude());
        m.put("longitude", img.getLongitude());
        m.put("height", img.getAltitude());
        // 图片URL: 优先知眸标注图(带框, 已落盘), 无标注图时兜底原图
        // 阶段2补充: 兜底路径按格式选前缀 - 二期({date}/{taskUuid}/original/xx)走 /image-detect-img/, 一期手动入口走 /zhimou-img/
        // 一结果一行: annotated_local_path 为单路径(该行app自己的标注图)
        String annotated = img.getAnnotatedLocalPath();
        String imageRelPath = StrUtil.isNotBlank(annotated) ? annotated : img.getLocalImagePath();
        String urlPrefix;
        if (StrUtil.isNotBlank(annotated)) {
            urlPrefix = "/api/image-detect-img/";
        } else if (StrUtil.isNotBlank(imageRelPath)) {
            urlPrefix = imageRelPath.split("/").length >= 3 ? "/api/image-detect-img/" : "/api/zhimou-img/";
        } else {
            urlPrefix = null;
        }
        m.put("image_http_url", urlPrefix != null ? urlPrefix + imageRelPath : "");
        m.put("image_has_osd", img.getLatitude() != null ? 1 : 0);
        m.put("image_width", img.getImageWidth());
        m.put("image_height", img.getImageHeight());
        m.put("wayline_uuid", "");
        m.put("local_image_name", img.getLocalImageName());
        m.put("clean_status", img.getCleanStatus());
        m.put("work_order_id", img.getWorkOrderId());
        m.put("status", img.getWorkOrderId() != null ? "handled" : "unhandled");
        m.put("hit_count", img.getHitCount() != null ? img.getHitCount() : 0);
        m.put("data_source", "image");
        return m;
    }

    private Map<String, Object> buildResultVO(ZhimouDetectionResult dr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dr.getId());
        m.put("zhimou_task_id", dr.getZhimouTaskId());
        m.put("aircraft_sn", dr.getAircraftSn());
        m.put("rule_id", dr.getRuleId());
        m.put("rule_name", dr.getRuleName());
        m.put("hit_count", dr.getHitCount());
        m.put("event_time", dr.getEventTime());
        m.put("event_timestamp_ms", dr.getEventTimestampMs());
        m.put("latitude", dr.getLatitude());
        m.put("longitude", dr.getLongitude());
        m.put("height", dr.getHeight());
        m.put("image_http_url", zhimouTaskService.buildImageHttpUrl(dr.getImageRelativePath()));
        m.put("image_has_osd", dr.getImageHasOsd());
        m.put("image_width", dr.getImageWidth());
        m.put("image_height", dr.getImageHeight());
        m.put("wayline_uuid", dr.getWaylineUuid() != null ? dr.getWaylineUuid() : "");
        m.put("work_order_id", dr.getWorkOrderId());
        return m;
    }

    /**
     * 恢复告警状态为未处理
     */
    @PostMapping("/results/{id}/restore")
    public R<?> restoreResult(@PathVariable Long id) {
        if (!zhimouTaskService.restoreResult(id)) {
            return R.fail("记录不存在或恢复失败");
        }
        return R.ok("恢复成功");
    }

    /**
     * 报警详情(用户点报警卡片时调, 拿完整targets_json)
     */
    @GetMapping("/results/{id}")
    public R<Map<String, Object>> resultDetail(@PathVariable Long id) {
        ZhimouDetectionResult dr = zhimouTaskService.getResultDetail(id);
        if (dr == null) return R.fail("记录不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dr.getId());
        m.put("zhimou_task_id", dr.getZhimouTaskId());
        m.put("aircraft_sn", dr.getAircraftSn());
        m.put("business_id", dr.getBusinessId());
        m.put("rule_id", dr.getRuleId());
        m.put("rule_name", dr.getRuleName());
        m.put("hit_count", dr.getHitCount());
        m.put("targets_json", dr.getTargetsJson());
        m.put("event_time", dr.getEventTime());
        m.put("event_timestamp_ms", dr.getEventTimestampMs());
        m.put("latitude", dr.getLatitude());
        m.put("longitude", dr.getLongitude());
        m.put("altitude", dr.getAltitude());
        m.put("height", dr.getHeight());
        m.put("gimbal_pitch", dr.getGimbalPitch());
        m.put("zoom_factor", dr.getZoomFactor());
        m.put("image_http_url", zhimouTaskService.buildImageHttpUrl(dr.getImageRelativePath()));
        m.put("image_format", dr.getImageFormat());
        m.put("image_width", dr.getImageWidth());
        m.put("image_height", dr.getImageHeight());
        m.put("image_has_osd", dr.getImageHasOsd());
        m.put("raw_callback_json", dr.getRawCallbackJson());
        m.put("create_time", dr.getCreateTime());
        return R.ok(m);
    }

    // =================================================================
    // 三-2、⭐ 数据统计: 按 target_id 聚合(targets_json 展开, 去重计数)
    // =================================================================

    /**
     * 数据统计-分页查询
     * 按 targets_json 中 target_id 去重, 数量=被识别次数
     * 每行关联最近一次识别的 result 记录(与告警管理同源图片)
     */
    @GetMapping("/statistics/page")
    public R<Map<String, Object>> statisticsPage(
            @RequestParam(value = "aircraft_sn", required = false) String aircraftSn,
            @RequestParam(value = "rule_name", required = false) String ruleName,
            @RequestParam(value = "device_name", required = false) String deviceName,
            @RequestParam(value = "route_name", required = false) String routeName,
            @RequestParam(value = "target_name", required = false) String targetName,
            @RequestParam(value = "target_color", required = false) String targetColor,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Map<String, Object> res = zhimouTaskService.statisticsByTarget(
                aircraftSn, ruleName, deviceName, routeName,
                targetName, targetColor, keyword,
                startTime, endTime, page, size);
        return R.ok(res);
    }

    /**
     * 数据统计-导出全量(供前端转 Excel)
     * 返回当前搜索条件下的全量聚合结果, 不分页
     */
    @GetMapping("/statistics/export")
    public R<List<Map<String, Object>>> statisticsExport(
            @RequestParam(value = "aircraft_sn", required = false) String aircraftSn,
            @RequestParam(value = "rule_name", required = false) String ruleName,
            @RequestParam(value = "device_name", required = false) String deviceName,
            @RequestParam(value = "route_name", required = false) String routeName,
            @RequestParam(value = "target_name", required = false) String targetName,
            @RequestParam(value = "target_color", required = false) String targetColor,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "start_time", required = false) String startTime,
            @RequestParam(value = "end_time", required = false) String endTime,
            @RequestParam(value = "max_size", defaultValue = "5000") int maxSize) {
        List<Map<String, Object>> list = zhimouTaskService.statisticsExport(
                aircraftSn, ruleName, deviceName, routeName,
                targetName, targetColor, keyword,
                startTime, endTime, maxSize);
        return R.ok(list);
    }

    /**
     * 数据统计-获取所有出现过的目标名称(distinct class)
     */
    @GetMapping("/statistics/target-names")
    public R<List<String>> distinctTargetNames() {
        return R.ok(zhimouTaskService.listDistinctTargetNames());
    }

    /**
     * 数据统计-获取所有出现过的目标颜色(distinct color, 不含空)
     */
    @GetMapping("/statistics/target-colors")
    public R<List<String>> distinctTargetColors() {
        return R.ok(zhimouTaskService.listDistinctTargetColors());
    }

    // =================================================================
    // 四、⭐ 给知眸回调: 知眸POST识别结果过来(核心!)
    // =================================================================

    /**
     * 知眸识别结果回调入口.
     * 创建任务时填的 report.httpurl 就指向这个接口的完整地址:
     *   http://{我们服务器IP}:8080/api/zhimou/callback
     */
    @PostMapping("/callback")
    public Map<String, Object> callback(HttpServletRequest request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String body = readRequestBody(request);
            log.info("[知眸-回调-入口] 收到回调, payload={}", body);
            JsonNode root;
            try {
                root = OBJECT_MAPPER.readTree(body);
            } catch (Exception e) {
                log.warn("[知眸-回调] JSON解析失败, 尝试当普通文本存");
                resp.put("code", -1);
                resp.put("msg", "JSON parse error");
                return resp;
            }
            zhimouTaskService.handleCallback(root);
            resp.put("code", 0);
            resp.put("msg", "success");
            return resp;
        } catch (Exception e) {
            log.error("[知眸-回调] 处理异常", e);
            resp.put("code", 500);
            resp.put("msg", e.getMessage());
            return resp;
        }
    }

    private String readRequestBody(HttpServletRequest req) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = req.getReader();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            log.warn("[知眸-回调] 读取请求体失败", e);
            return "";
        }
    }
}
