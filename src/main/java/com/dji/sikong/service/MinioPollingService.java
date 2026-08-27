package com.dji.sikong.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.dji.sikong.config.DjiOpenApiProperties;
import com.dji.sikong.config.MinioProperties;
import com.dji.sikong.config.ZhimouProperties;
import com.dji.sikong.openapi.DjiOpenApiClient;
import com.dji.sikong.mapper.ZhimouImageDetectionMapper;
import com.dji.sikong.mapper.SelfModelImageDetectionMapper;
import com.dji.sikong.entity.ZhimouImageDetection;
import com.dji.sikong.entity.SelfModelImageDetection;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sikong.service.facade.BatchProcessResult;
import com.fasterxml.jackson.databind.JsonNode;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段3核心：MinIO 轮询扫描飞行任务图片批次（@Scheduled 固定延迟）
 * <p>
 * 整体流程（每 30s 执行一次）：
 *   1. TaskSwitchService.isImageTaskEnabled() == 关 → 跳过
 *   2. MinIO.listObjects(fh_scanPrefix) → 用正则提取各 {TASK_UUID} 并按 task 分组
 *   3. 对每个 TASK_UUID：
 *      a. Redis minio:flight:{tuuid} 存在 (PROCESSING/COMPLETED) → 跳过
 *      b. 调 getFlightTaskInfo → folder_info.uploaded != expected → 跳（下轮重试）
 *      c. 调 getFlightTaskMedia → 数量 != MinIO 实际数量 → 跳（下轮重试）
 *      d. SETNX minio:flight:{tuuid}=PROCESSING TTL=30min  → 失败则跳
 *      e. 调 getFlightTaskPiecesDetail → flight_pieces[0].drone_sn (真实SN)
 *      f. MinIO.getObject 批量下载到 {temp-dir}/{tuuid}/filename
 *      g. ImageRecognitionFacade.processImageBatch(..., customLocalImagePrefix=TASK_UUID, aircraftSn=droneSn)
 *         -> 启动知眸+自有模型并行线程
 *      h. 方法 return（不等回调到达；回调到达后由 ZhimouTaskServiceImpl.triggerImageCleanup
 *         与 SelfModelDetectService.triggerImageCleanupIfReadyForSelf 自动推进到完成）
 * <p>
 * 重试/防重：
 *   - PROCESSING TTL 30min；若超时还没COMPLETED，下一轮SETNX会抢锁重试
 *   - COMPLETED 永久 key，后续永远跳过（运维要重跑需要先DEL这个key，或调用 MinioController.retry）
 */
@Slf4j
@Service
public class MinioPollingService {

    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioProperties minioProperties;

    @Resource
    private DjiOpenApiProperties djiOpenApiProperties;

    @Resource
    private ZhimouProperties zhimouProperties;

    @Resource
    private TaskSwitchService taskSwitchService;

    @Resource
    private DjiOpenApiClient djiOpenApiClient;

    @Resource
    private ImageRecognitionFacade imageRecognitionFacade;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ImageCleanupService imageCleanupService;

    @Resource
    private ZhimouImageDetectionMapper zhimouImageDetectionMapper;

    @Resource
    private SelfModelImageDetectionMapper selfModelImageDetectionMapper;

    // 全局互斥锁：确保同一时间只有一个批次在处理（知眸并发仅2个）
    private final AtomicBoolean processingLock = new AtomicBoolean(false);

    // fh_sync/{ORG_UUID}/{PROJECT_UUID}/media/{TASK_UUID}/{FILENAME.ext}
    private volatile Pattern pathRegex = null;

    private Pattern buildRegex() {
        if (pathRegex != null) return pathRegex;
        synchronized (this) {
            if (pathRegex != null) return pathRegex;
            String org = djiOpenApiProperties.getDefaultOrganizationUuid();
            String proj = djiOpenApiProperties.getDefaultProjectUuid();
            if (StrUtil.isBlank(org) || StrUtil.isBlank(proj)) {
                // 配置还没就绪 → 返回一个永远不匹配的正则（避免空指针）
                pathRegex = Pattern.compile("^NEVER_MATCH_$");
            } else {
                // regex 中的 UUID 需要对内部 - 不转义，{} 也不转义
                pathRegex = Pattern.compile("^" + Pattern.quote(minioProperties.getScanPrefix()
                        == null ? "fh_sync/" : minioProperties.getScanPrefix())
                        + org + "/" + proj + "/media/([^/]+)/(.+\\.(?i:jpe?g|png))$");
            }
            return pathRegex;
        }
    }

    /**
     * 主入口：Spring @Scheduled 按配置 poll-interval-ms（默认 30s）触发
     * fixedDelay：上次执行 END → 下次 START 的间隔（避免长轮询任务重入）
     */
    @Scheduled(fixedDelay = 30000)
    public void scanAndProcess() {
        try {
            if (!taskSwitchService.isImageTaskEnabled()) {
                log.debug("[MinIO轮询] image_task 已关闭，本轮跳过");
                return;
            }
            if (minioClient == null) {
                log.debug("[MinIO轮询] MinioClient Bean 未初始化（endpoint未配置），跳过");
                return;
            }
            // 全局互斥锁：确保 @Scheduled 与 retry 不同时执行
            if (!processingLock.compareAndSet(false, true)) {
                log.debug("[MinIO轮询] 上一轮处理仍在进行，跳过本轮");
                return;
            }
            try {
                doScanAndProcess();
            } finally {
                processingLock.set(false);
            }
        } catch (Throwable t) {
            // 兜底：定时任务任何异常不得影响下一轮调度
            log.error("[MinIO轮询] scanAndProcess 顶层异常", t);
            processingLock.set(false);
        }
    }

    private void doScanAndProcess() {
        // ===== 1. 扫描 MinIO 目录 =====
        List<TaskGroup> groups = listAndGroupTaskUuids();
        if (groups == null || groups.isEmpty()) {
            log.debug("[MinIO轮询] 暂无飞行任务图片目录（0个批次）");
            return;
        }
        log.info("[MinIO轮询] 扫描到 {} 个候选飞行任务批次, 开始逐个校验", groups.size());

        for (TaskGroup g : groups) {
            try {
                processOneGroup(g);
            } catch (Exception e) {
                log.error("[MinIO轮询] 处理批次异常 taskUuid={}", g.taskUuid, e);
            }
        }
    }

    /**
     * 按 TASK_UUID 分组： fh_sync/ORG/PROJ/media/{TASK_UUID}/{filename}
     */
    private List<TaskGroup> listAndGroupTaskUuids() {
        String bucket = minioProperties.getBucket();
        String prefix = minioProperties.getScanPrefix() != null ? minioProperties.getScanPrefix() : "fh_sync/";
        Pattern regex = buildRegex();
        Map<String, TaskGroup> map = new LinkedHashMap<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build());
            for (Result<Item> r : results) {
                Item item;
                try { item = r.get(); } catch (Exception e) { log.warn("[MinIO轮询] 读Item异常 {}", e.getMessage()); continue; }
                if (item.isDir()) continue;
                String objectKey = item.objectName();
                Matcher m = regex.matcher(objectKey);
                if (!m.matches()) continue;
                String taskUuid = m.group(1);
                String filename = m.group(2);
                TaskGroup g = map.computeIfAbsent(taskUuid, k -> new TaskGroup(taskUuid));
                g.files.add(new TaskFile(objectKey, filename, item.lastModified() != null ? item.lastModified().toInstant().toEpochMilli() : 0L));
            }
        } catch (Exception e) {
            log.error("[MinIO轮询] 扫描 MinIO 失败 bucket={}, prefix={}", bucket, prefix, e);
            return Collections.emptyList();
        }
        return new ArrayList<>(map.values());
    }

    // ----------------------------------------------------------------
    // 单批次处理
    // ----------------------------------------------------------------
    private void processOneGroup(TaskGroup g) {
        String tuuid = g.taskUuid;

        // (1) 批状态：已经 PROCESSING/COMPLETED → 跳过
        String batchKey = "minio:flight:" + tuuid;
        String batchState = stringRedisTemplate.opsForValue().get(batchKey);
        if (batchState != null) {
            if ("COMPLETED".equals(batchState)) return;
            if ("PROCESSING".equals(batchState)) return;
            // 其他未知值覆盖
        }

        // (2) 完整性校验：folder_info expected vs uploaded
        if (minioProperties.isValidateUploadCountBeforeProcess()) {
            JsonNode info = djiOpenApiClient.getFlightTaskInfo(tuuid);
            if (info != null) {
                int code = info.path("code").asInt(-1);
                if (code != 0) {
                    log.warn("[MinIO轮询] taskUuid={} getFlightTaskInfo 非0 code={}, msg={}, 跳过本轮",
                            tuuid, code, safeSubstr(info.path("message")));
                    return;
                }
                JsonNode folderInfo = info.path("data").path("folder_info");
                int expected = folderInfo.path("expected_file_count").asInt(-1);
                int uploaded = folderInfo.path("uploaded_file_count").asInt(-1);
                if (expected <= 0 || uploaded < expected) {
                    log.info("[MinIO轮询] taskUuid={} 仍在同步中 (uploaded={}/expected={})，跳过本轮",
                            tuuid, uploaded, expected);
                    return;
                }
            }
            // 与 media 接口数量二次对齐
            JsonNode media = djiOpenApiClient.getFlightTaskMedia(tuuid);
            int mediaCnt = 0;
            if (media != null && media.path("code").asInt(-1) == 0) {
                JsonNode arr = media.path("data").has("list") ? media.path("data").path("list") : media.path("data");
                if (arr.isArray()) mediaCnt = arr.size();
            }
            if (mediaCnt > 0 && mediaCnt != g.files.size()) {
                log.info("[MinIO轮询] taskUuid={} media接口数={} != MinIO数={}，同步未完成跳过本轮",
                        tuuid, mediaCnt, g.files.size());
                return;
            }
        }

        // (3) 抢分布式锁：SETNX PROCESSING TTL
        int lockTtl = minioProperties.getBatchLockTtlSec() > 0 ? minioProperties.getBatchLockTtlSec() : 1800;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(batchKey, "PROCESSING",
                Duration.ofSeconds(lockTtl));
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("[MinIO轮询] taskUuid={} 已被其他实例锁定，跳过", tuuid);
            return;
        }
        log.info("[MinIO轮询] ✅ 拿到处理锁 taskUuid={}, 图片数={}, TTL={}s", tuuid, g.files.size(), lockTtl);

        try {
            // (4) 拿真实 drone_sn（detail API）
            JsonNode detail = djiOpenApiClient.getFlightTaskPiecesDetail(
                    djiOpenApiProperties.getDefaultProjectUuid(), tuuid);
            String droneSn = null;
            String taskName = null;
            if (detail != null && detail.path("code").asInt(-1) == 0) {
                JsonNode data = detail.path("data");
                JsonNode pieces = data.has("flight_pieces") ? data.path("flight_pieces") : data.path("flight_piece");
                JsonNode piece0 = pieces.isArray() && pieces.size() > 0 ? pieces.get(0) : (pieces.isObject() ? pieces : null);
                if (piece0 != null) {
                    droneSn = piece0.path("drone_sn").asText(null);
                    taskName = piece0.path("task_name").asText(null);
                    if (StrUtil.isBlank(droneSn)) {
                        droneSn = piece0.path("drone_info").path("device_sn").asText(null);
                    }
                }
            }
            if (StrUtil.isBlank(droneSn)) {
                log.error("[MinIO轮询] taskUuid={} 无法从 detail API 取到 drone_sn，跳过（该批次需要手动清理 PROCESSING 锁）",
                        tuuid);
                // 失败释放锁，让下一轮/其他实例重试
                stringRedisTemplate.delete(batchKey);
                return;
            }
            log.info("[MinIO轮询] taskUuid={} 飞行任务名={}, SN={}", tuuid, taskName, droneSn);

            // (5) 下载到 tempDir/tuuid/filename
            List<File> localFiles = downloadBatch(tuuid, g.files);
            if (localFiles == null || localFiles.isEmpty()) {
                log.error("[MinIO轮询] taskUuid={} 下载失败或无图片，释放锁重试", tuuid);
                stringRedisTemplate.delete(batchKey);
                return;
            }

            // (5.1) 为每张下载成功的图片创建初始 Redis key，防止 tryCompleteMinioBatch 过早判断批次完成
            for (File file : localFiles) {
                String fileName = file.getName();
                int dotIdx = fileName.lastIndexOf('.');
                String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
                String localImageName = tuuid + "/" + baseName;
                String imgKey = "minio:flight:img:" + localImageName;
                Map<String, String> initMeta = new HashMap<>();
                initMeta.put("status", "pending");  // 标记为待处理
                stringRedisTemplate.opsForHash().putAll(imgKey, initMeta);
                stringRedisTemplate.expire(imgKey, 7, TimeUnit.DAYS);
            }
            log.info("[MinIO轮询] taskUuid={} 已创建 {} 个初始 Redis key", tuuid, localFiles.size());

            // (6) 分派给统一 Facade（内部并行线程执行知眸GPU上报 + 自有模型CPU识别）
            String appId = zhimouProperties.getOpenapi() != null ? zhimouProperties.getOpenapi().getDefaultAppId() : null;
            String pkgId = zhimouProperties.getOpenapi() != null ? zhimouProperties.getOpenapi().getDefaultPkgId() : null;
            String imageBaseDir = zhimouProperties.getImage() != null ? zhimouProperties.getImage().getSaveRootDir() : null;

            BatchProcessResult result = imageRecognitionFacade.processImageBatch(
                    localFiles, droneSn, appId, pkgId,
                    new BigDecimal("0.5"), imageBaseDir, tuuid,
                    true, true, 1000L, 500L);

            if (!result.isSuccess()) {
                log.error("[MinIO轮询] taskUuid={} Facade.processImageBatch 提交失败: {}, 释放锁",
                        tuuid, result.getErrorMsg());
                stringRedisTemplate.delete(batchKey);
                return;
            }
            log.info("[MinIO轮询] ✅ 分派完成 taskUuid={}, 图片数={}, zhimouTaskId={}, self(成功/失败)={}/{}",
                    tuuid, result.getImageCount(), result.getZhimouTaskId(),
                    result.getSelfModelSuccess(), result.getSelfModelFail());

            // 成功分派后：PROCESSING 锁继续保留 (TTL 30min)，后续由回调/入库触发清洗，
            // 最后一张图清洗完成后 ZhimouTaskServiceImpl.tryMarkBatchCompleteByPrefix
            // -> ImageCleanupService.tryCompleteMinioBatch 改为 COMPLETED 永久 + 删temp目录。
            // 如果 30min 内没走完，锁过期，下一轮重跑（重跑会覆盖下载temp目录，cleanupSingleImage
            // 是按 local_image_name 幂等更新，不会重复插入；知眸 taskId 是新建的不同 task，不会
            // 串回调到 Redis meta（不同 zhimou taskId），清洗只认 zhimou 入库记录的
            // local_image_name，理论上不会错配，但可能产生重复识别。这是权衡（30min够100张图片）。

        } catch (Exception e) {
            log.error("[MinIO轮询] 处理批次异常 taskUuid={}", tuuid, e);
            // 异常解锁避免死锁
            stringRedisTemplate.delete(batchKey);
        }
    }

    private List<File> downloadBatch(String taskUuid, List<TaskFile> files) {
        String tempDir = minioProperties.getTempDir();
        if (StrUtil.isBlank(tempDir)) {
            // 没配置 → 兜底用 {zhimou.image.saveRootDir}/minio-incoming
            tempDir = (zhimouProperties.getImage() != null ? zhimouProperties.getImage().getSaveRootDir() : "./tmp")
                    + "/minio-incoming";
        }
        Path batchDir = Paths.get(tempDir, taskUuid);
        try {
            Files.createDirectories(batchDir);
            // 如果上次残留了旧文件 → 先清空，确保下载干净
            if (batchDir.toFile().list() != null && batchDir.toFile().list().length > 0) {
                log.warn("[MinIO轮询] taskUuid={} 临时目录非空，先清理后再下载", taskUuid);
                for (File f : batchDir.toFile().listFiles()) FileUtil.del(f);
            }
        } catch (Exception e) {
            log.error("[MinIO轮询] 创建 temp-dir 失败 {}", batchDir, e);
            return Collections.emptyList();
        }
        List<File> out = new ArrayList<>(files.size());
        int ok = 0, fail = 0;
        for (TaskFile tf : files) {
            Path target = batchDir.resolve(tf.filename);
            try {
                if (!target.toFile().exists()) {
                    try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(tf.objectKey)
                            .build())) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                out.add(target.toFile());
                ok++;
            } catch (Exception e) {
                fail++;
                log.warn("[MinIO轮询] 下载失败 objectKey={}, taskUuid={}", tf.objectKey, taskUuid, e);
            }
        }
        log.info("[MinIO轮询] 下载 taskUuid={} 成功={}/失败={}, 目录={}", taskUuid, ok, fail, batchDir);
        return out;
    }

    // =================================================================
    // 对外 API 辅助（运维接口 / 手动重试）
    // =================================================================

    /**
     * 手动重跑某批次（MinioController.retry 调用）
     * <p>
     * 策略：先查 DB 是否已有该批次的知眸/自有模型记录
     *   - 两表都有记录 → 直接调 cleanupSingleImage 清洗（不重新上报到知眸，避免去重）
     *   - 没有记录 → 走正常 processOneGroup 流程
     */
    public boolean retryBatch(String taskUuid) {
        if (StrUtil.isBlank(taskUuid)) return false;
        // 互斥锁：等待 @Scheduled 释放（最多等 60s）
        int wait = 0;
        while (!processingLock.compareAndSet(false, true)) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            if (++wait > 60) {
                log.warn("[MinIO轮询] retryBatch 等待互斥锁超时 taskUuid={}", taskUuid);
                return false;
            }
        }
        try {
            return doRetryBatch(taskUuid);
        } finally {
            processingLock.set(false);
        }
    }

    private boolean doRetryBatch(String taskUuid) {
        stringRedisTemplate.delete("minio:flight:" + taskUuid);
        Set<String> imgKeys = stringRedisTemplate.keys("minio:flight:img:" + taskUuid + "*");
        if (imgKeys != null && !imgKeys.isEmpty()) stringRedisTemplate.delete(imgKeys);

        // 查 DB 是否已有该批次的记录
        List<ZhimouImageDetection> zRecs = zhimouImageDetectionMapper.selectList(
                new LambdaQueryWrapper<ZhimouImageDetection>()
                        .likeRight(ZhimouImageDetection::getLocalImageName, taskUuid + "/")
                        .eq(ZhimouImageDetection::getDeleted, 0));
        List<SelfModelImageDetection> sRecs = selfModelImageDetectionMapper.selectList(
                new LambdaQueryWrapper<SelfModelImageDetection>()
                        .likeRight(SelfModelImageDetection::getLocalImageName, taskUuid + "/")
                        .eq(SelfModelImageDetection::getDeleted, 0));

        boolean hasZhimou = zRecs != null && !zRecs.isEmpty();
        boolean hasSelf = sRecs != null && !sRecs.isEmpty();
        log.info("[MinIO轮询] 运维手动重试 taskUuid={}, DB记录: zhimou={}条, self={}条",
                taskUuid, hasZhimou ? zRecs.size() : 0, hasSelf ? sRecs.size() : 0);

        if (hasZhimou || hasSelf) {
            // DB 已有记录 → 直接清洗，不重新上报（避免知眸去重导致回调不到达）
            log.info("[MinIO轮询] taskUuid={} DB已有记录，直接执行清洗（不重新上报知眸）", taskUuid);
            Set<String> localNames = new TreeSet<>();
            for (ZhimouImageDetection z : zRecs) localNames.add(z.getLocalImageName());
            for (SelfModelImageDetection s : sRecs) localNames.add(s.getLocalImageName());
            int cleaned = 0;
            for (String ln : localNames) {
                try {
                    String result = imageCleanupService.cleanupSingleImage(ln);
                    log.info("[MinIO轮询] retry清洗 localImageName={}, result={}", ln, result);
                    cleaned++;
                } catch (Exception e) {
                    log.warn("[MinIO轮询] retry清洗异常 localImageName={}", ln, e);
                }
            }
            // 标记批次完成
            imageCleanupService.tryCompleteMinioBatch(taskUuid);
            log.info("[MinIO轮询] taskUuid={} retry清洗完成，共{}张", taskUuid, cleaned);
            return true;
        }

        // DB 无记录 → 走正常处理流程
        log.info("[MinIO轮询] taskUuid={} DB无记录，重新上报处理", taskUuid);
        List<TaskGroup> groups = listAndGroupTaskUuids();
        if (groups != null) {
            for (TaskGroup g : groups) {
                if (taskUuid.equals(g.taskUuid)) {
                    try { processOneGroup(g); return true; } catch (Exception e) {
                        log.error("[MinIO轮询] retryBatch processOneGroup 异常 taskUuid={}", taskUuid, e);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** 运维：列出当前批次状态 */
    public Map<String, String> listBatchStates() {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> keys = stringRedisTemplate.keys("minio:flight:*");
        if (keys == null) return out;
        for (String k : keys) {
            if (k.startsWith("minio:flight:img:")) continue;
            String v = stringRedisTemplate.opsForValue().get(k);
            out.put(k.substring("minio:flight:".length()), v);
        }
        return out;
    }

    // =================================================================
    // 内部 DTO
    // =================================================================
    private static class TaskGroup {
        final String taskUuid;
        final List<TaskFile> files = new ArrayList<>();
        TaskGroup(String taskUuid) { this.taskUuid = taskUuid; }
    }

    private static class TaskFile {
        final String objectKey;
        final String filename;
        final long modifiedMs;
        TaskFile(String objectKey, String filename, long modifiedMs) {
            this.objectKey = objectKey;
            this.filename = filename;
            this.modifiedMs = modifiedMs;
        }
    }

    private static String safeSubstr(JsonNode n) {
        if (n == null || n.isNull()) return "";
        String s = n.asText("");
        return s.length() <= 120 ? s : s.substring(0, 120);
    }
}
