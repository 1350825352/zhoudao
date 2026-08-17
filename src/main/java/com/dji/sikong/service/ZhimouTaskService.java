package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.dto.StartLiveStreamDTO;
import com.dji.sikong.entity.LiveStream;
import com.dji.sikong.entity.ZhimouDetectionResult;
import com.dji.sikong.entity.ZhimouTask;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * 知眸AI识别平台业务服务
 */
public interface ZhimouTaskService extends IService<ZhimouTask> {

    // ================ 应用(包)查询: 给前端下拉选择 ================
    /** 查询应用包列表(GET /api/app) */
    JsonNode listApplicationPackages();

    /** 根据应用包ID查询其下的应用(规则)列表 */
    JsonNode listAppsByPackage(String applicationPackageId);

    /** 获取配置文件中的默认应用ID(知眸接口不可用时给前端提示当前配置) */
    String getDefaultAppId();

    /** 获取配置文件中的默认应用包ID(知眸接口不可用时给前端提示当前配置) */
    String getDefaultPkgId();

    // ================ 任务生命周期 ================
    /**
     * 飞机起飞开启直播成功后, 创建知眸AI识别任务(视频模式).
     * 前提: liveStream.push_url/pull_url 已经有值.
     * @param liveStream 直播记录
     * @param appId 前端选的应用ID
     * @param applicationPackageId 前端选的应用包ID(可选)
     * @return 知眸返回的 task_id; 失败返回 null
     */
    String createVideoTaskWhenLiveStart(LiveStream liveStream, String appId, String applicationPackageId);

    /** 飞机降落关流时, 关闭知眸AI任务(DELETE /api/task/{taskId}) */
    boolean closeZhimouTaskByAircraftSn(String aircraftSn);

    /** 根据 liveStreamId 关闭知眸任务 */
    boolean closeZhimouTaskByLiveStreamId(Long liveStreamId);

    /** 服务启动时: 清理知眸侧还在跑但本地已停止的僵尸任务 */
    void cleanupZombieTasksOnStartup();

    /** 清理超过24小时的直播流和知眸任务历史记录(软删除) */
    int cleanupOldHistoryRecords(int retainHours);

    // ================ 飞行位置上报(OSD数据变动时调用) ================
    /**
     * 上报飞机位置/姿态到知眸(让识别结果关联GPS).
     * 内部会按 flight-report-interval-ms 配置节流(避免上报太频繁).
     */
    void reportFlightIfNeeded(String aircraftSn,
                              Double latitude, Double longitude, Double altitude, Double height,
                              Double gimbalPitch, Double zoomFactor, Long timestampMs);

    // ================ 回调处理: 知眸POST调我们 ================
    /**
     * 处理知眸回调:
     *   1. 解析image节点 -> Base64解码或下载 -> 按年月日目录保存到本机 -> 返回相对路径
     *   2. 目标、GPS、时间等写 zhimou_detection_result 表
     *   3. 通过MQTT广播给前端(让页面实时刷新)
     */
    ZhimouDetectionResult handleCallback(JsonNode root);

    // ================ 给前端查询接口 ================
    /** 前端进入页面拉取最近N条识别结果 */
    List<ZhimouDetectionResult> listLatestResults(String aircraftSn, int limit);
    
    Page<ZhimouDetectionResult> pageResults(String aircraftSn, String ruleId, String ruleName, String deviceName, String routeName, String status, String startTime, String endTime, int page, int size);

    /** 报警详情 */
    ZhimouDetectionResult getResultDetail(Long id);
    
    /** 恢复告警状态为未处理 */
    boolean restoreResult(Long id);

    /** 给前端用: 把DB里的imageRelativePath拼成完整HTTP相对URL(含context-path和url-prefix) */
    String buildImageHttpUrl(String imageRelativePath);

    /**
     * 数据统计: 按 target_id 聚合统计(targets_json 中展开, 去重计数)
     * 每行 = 一个唯一 target_id, 数量 = 该目标被识别的次数
     * 每行关联最近一次识别的那条 result 记录的图片(与告警管理同源)
     * @param aircraftSn 飞机SN
     * @param ruleName 告警类型(rule_name)
     * @param deviceName 飞机名称
     * @param routeName 航线名称
     * @param targetName 目标名称(targets_json 中 class 字段)
     * @param targetColor 目标颜色(targets_json 中 color 字段,"无"表示空)
     * @param keyword 关键字(按 target_id 模糊)
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param page 页码
     * @param size 每页大小
     * @return 聚合统计结果(含 list/total)
     */
    Map<String, Object> statisticsByTarget(String aircraftSn, String ruleName, String deviceName, String routeName,
                                            String targetName, String targetColor, String keyword,
                                            String startTime, String endTime, int page, int size);

    /**
     * 数据统计-导出全量: 按搜索条件返回全量聚合结果(不分页, 供前端转 Excel)
     * 参数同 statisticsByTarget, 但 page/size 由调用方处理
     */
    List<Map<String, Object>> statisticsExport(String aircraftSn, String ruleName, String deviceName, String routeName,
                                              String targetName, String targetColor, String keyword,
                                              String startTime, String endTime, int maxSize);

    /**
     * 数据统计-获取所有出现过的目标名称(distinct class)
     */
    List<String> listDistinctTargetNames();

    /**
     * 数据统计-获取所有出现过的目标颜色(distinct color, 不含空)
     */
    List<String> listDistinctTargetColors();

    // ================ 扩展: 图片任务(后续抽帧时用,先预留) ================
    /** 创建图片识别任务(mode=image) */
    String createImageTask(String aircraftSn, String appId, String applicationPackageId);

    /** 图片任务: 上报一张图片(对应 /api/task/{taskId}/image) */
    JsonNode reportImage(String zhimouTaskId, long imageId, String aircraftSn, byte[] imageBytes);

    /** 健康检查用: 查询当前活跃的码流(开启中/已开启) */
    List<LiveStream> listActiveLiveStreamsNeedZhimou();

    /** 查询知眸服务器上的所有任务列表 */
    JsonNode listTasks();

    /** 删除指定的知眸任务 */
    JsonNode deleteTask(String taskId);

    // ================ 延迟调度(3秒) ================
    /**
     * 码流转发已开启 → 写入「创建知眸任务」的3秒延迟队列.
     * 到时间后由调度器自动调用 createVideoTaskWhenLiveStart.
     */
    void scheduleTaskOpenAfterLiveStart(LiveStream liveStream, String appId, String applicationPackageId);

    /**
     * 码流转发已关闭 → 写入「关闭知眸任务」的3秒延迟队列.
     * 到时间后由调度器自动调用 closeZhimouTaskByAircraftSn/LiveStreamId.
     */
    void scheduleTaskCloseAfterLiveStop(LiveStream liveStream);
}
