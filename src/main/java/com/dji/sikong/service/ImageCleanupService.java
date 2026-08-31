package com.dji.sikong.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dji.sikong.config.MinioProperties;
import com.dji.sikong.entity.SelfModelImageDetection;
import com.dji.sikong.entity.ZhimouImageDetection;
import com.dji.sikong.mapper.SelfModelImageDetectionMapper;
import com.dji.sikong.mapper.ZhimouImageDetectionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图片识别数据清洗服务 (二期新增)
 * <p>
 * 核心思路: 同一张图片(local_image_name相同)在 zhimou_image_detection 和
 *   self_model_image_detection 两表都识别出目标, 且目标位置匹配, 则判定为误识别.
 * <p>
 * bbox 距离匹配算法:
 *   知眸 bbox {x,y}        → 中心点 (x, y)
 *   自有  bbox {x1,y1,x2,y2} → 中心点 ((x1+x2)/2, (y1+y2)/2)
 *   距离 < 50px 视为同一目标 (阈值可调)
 */
@Service
@Slf4j
public class ImageCleanupService {

    /** 中心点距离匹配阈值(像素), 1920x1080 下约占 2.6% */
    private static final double DEFAULT_DISTANCE_THRESHOLD = 50.0;

    @Resource
    private ZhimouImageDetectionMapper zhimouImageDetectionMapper;

    @Resource
    private SelfModelImageDetectionMapper selfModelImageDetectionMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MinioProperties minioProperties;

    /**
     * 清洗单张图片
     *
     * @param localImageName 本地图片文件名(数据清洗关联键)
     * @return 清洗结果说明, 如 "误识别" / "正常" / "知眸无结果" / "未处理"
     */
    public String cleanupSingleImage(String localImageName) {
        return cleanupSingleImage(localImageName, DEFAULT_DISTANCE_THRESHOLD);
    }

    public String cleanupSingleImage(String localImageName, double distanceThreshold) {
        if (StrUtil.isBlank(localImageName)) {
            return "localImageName为空";
        }
        log.info("[清洗] 开始清洗 localImageName={}", localImageName);

        // 1. 查知眸表
        List<ZhimouImageDetection> zRecs = zhimouImageDetectionMapper.selectList(
                new LambdaQueryWrapper<ZhimouImageDetection>()
                        .eq(ZhimouImageDetection::getLocalImageName, localImageName)
                        .eq(ZhimouImageDetection::getDeleted, 0)
                        .orderByDesc(ZhimouImageDetection::getId));
        // 2. 查自有模型表
        List<SelfModelImageDetection> sRecs = selfModelImageDetectionMapper.selectList(
                new LambdaQueryWrapper<SelfModelImageDetection>()
                        .eq(SelfModelImageDetection::getLocalImageName, localImageName)
                        .eq(SelfModelImageDetection::getDeleted, 0)
                        .orderByDesc(SelfModelImageDetection::getId));

        // 情况 B/C: 只有一侧有结果 → 正常识别
        if (zRecs.isEmpty() && sRecs.isEmpty()) {
            log.info("[清洗] 两表均无记录, 无需清洗 localImageName={}", localImageName);
            return "两表均无记录";
        }
        if (sRecs.isEmpty() || sRecs.stream().noneMatch(s -> s.getCallStatus() != null && s.getCallStatus() == 1 && s.getHitCount() != null && s.getHitCount() > 0)) {
            // 自有模型无结果或调用失败
            String result = "自有模型无有效结果, 判定正常";
            markCleanStatus(zRecs, sRecs, 1, result);
            log.info("[清洗] {} localImageName={}", result, localImageName);
            return result;
        }
        if (zRecs.isEmpty() || zRecs.stream().noneMatch(z -> z.getHitCount() != null && z.getHitCount() > 0)) {
            String result = "知眸无有效结果, 判定正常";
            markCleanStatus(zRecs, sRecs, 1, result);
            log.info("[清洗] {} localImageName={}", result, localImageName);
            return result;
        }

        // 3. 提取两侧最新一条有效记录的目标中心点
        ZhimouImageDetection zLatest = zRecs.stream()
                .filter(z -> z.getHitCount() != null && z.getHitCount() > 0)
                .findFirst().orElse(null);
        SelfModelImageDetection sLatest = sRecs.stream()
                .filter(s -> s.getCallStatus() != null && s.getCallStatus() == 1 && s.getHitCount() != null && s.getHitCount() > 0)
                .findFirst().orElse(null);
        if (zLatest == null || sLatest == null) {
            String result = "未找到有效记录, 跳过";
            log.warn("[清洗] {} localImageName={}", result, localImageName);
            return result;
        }

        List<double[]> zCenters = extractZhimouCenters(zLatest.getTargetsJson());
        List<double[]> sCenters = extractSelfModelCenters(sLatest.getTargetsJson());
        log.info("[清洗] localImageName={}, 知眸目标数={}, 自有目标数={}",
                localImageName, zCenters.size(), sCenters.size());

        // 4. 逐个知眸目标, 找自有模型中最近的目标, 判断是否匹配
        int matchedZ = 0;
        for (double[] zc : zCenters) {
            double bestDist = Double.MAX_VALUE;
            for (double[] sc : sCenters) {
                double dist = Math.sqrt(
                        Math.pow(zc[0] - sc[0], 2) + Math.pow(zc[1] - sc[1], 2));
                if (dist < bestDist) bestDist = dist;
            }
            if (bestDist < distanceThreshold) {
                matchedZ++;
                log.info("[清洗] 知眸目标({},{}) 与自有模型最近目标距离={} < {} → 匹配",
                        zc[0], zc[1], String.format("%.2f", bestDist), distanceThreshold);
            } else {
                log.info("[清洗] 知眸目标({},{}) 最近距离={} >= {} → 不匹配",
                        zc[0], zc[1], String.format("%.2f", bestDist), distanceThreshold);
            }
        }

        // 5. 综合判定: 所有知眸目标都有匹配的自有模型目标 → 误识别; 否则正常
        String result;
        int cleanStatus;
        if (matchedZ > 0 && matchedZ == zCenters.size()) {
            // 情况 A: 两边都有结果且全部目标匹配 → 误识别
            result = String.format("误识别(知眸%d个目标全部匹配自有模型)", matchedZ);
            cleanStatus = 2;
        } else {
            // 部分匹配 → 正常
            result = String.format("正常(知眸%d个目标中%d个匹配自有模型)", zCenters.size(), matchedZ);
            cleanStatus = 1;
        }
        markCleanStatus(zRecs, sRecs, cleanStatus, result);
        log.info("[清洗] 完成 localImageName={}, 结果={}, cleanStatus={}",
                localImageName, result, cleanStatus);
        return result;
    }

    /**
     * 批量清洗: 扫描所有 clean_status=0 的记录涉及的 local_image_name
     */
    public int cleanupAllPending() {
        // 查所有 clean_status=0 的图片名(两表合并去重)
        List<String> names = new ArrayList<>();
        List<ZhimouImageDetection> zRecs = zhimouImageDetectionMapper.selectList(
                new LambdaQueryWrapper<ZhimouImageDetection>()
                        .eq(ZhimouImageDetection::getCleanStatus, 0)
                        .eq(ZhimouImageDetection::getDeleted, 0)
                        .isNotNull(ZhimouImageDetection::getLocalImageName));
        for (ZhimouImageDetection z : zRecs) {
            if (!names.contains(z.getLocalImageName())) names.add(z.getLocalImageName());
        }
        List<SelfModelImageDetection> sRecs = selfModelImageDetectionMapper.selectList(
                new LambdaQueryWrapper<SelfModelImageDetection>()
                        .eq(SelfModelImageDetection::getCleanStatus, 0)
                        .eq(SelfModelImageDetection::getDeleted, 0)
                        .isNotNull(SelfModelImageDetection::getLocalImageName));
        for (SelfModelImageDetection s : sRecs) {
            if (!names.contains(s.getLocalImageName())) names.add(s.getLocalImageName());
        }

        log.info("[清洗] 待清洗图片数={}", names.size());
        int cleaned = 0;
        for (String name : names) {
            try {
                cleanupSingleImage(name);
                cleaned++;
            } catch (Exception e) {
                log.error("[清洗] 异常 localImageName={}", name, e);
            }
        }
        log.info("[清洗] 批量清洗完成, 成功={}", cleaned);
        return cleaned;
    }

    // ================================================================
    // 阶段3新增：MinIO 批次状态流转辅助
    // ================================================================

    /**
     * 阶段3新增：当某张图完成清洗后调用：
     *   扫描 minio:flight:img:{taskUuid}* 是否全部被清理（keys 为空）
     *     是 → minio:flight:{taskUuid} = COMPLETED（永久）
     *          且 cleanupTempAfter=true 时删除 {minio.temp-dir}/{taskUuid} 下载目录
     *   幂等：两次调也没事（SET 覆盖相同、文件删除第二次返回true）。
     *
     * @param taskUuid 飞行任务UUID（fh_sync 路径里的 media/ 下那个 UUID）
     */
    public void tryCompleteMinioBatch(String taskUuid) {
        if (StrUtil.isBlank(taskUuid)) return;
        try {
            // 1. 仍有未完成的图片级key？
            Set<String> pendingKeys = stringRedisTemplate.keys("minio:flight:img:" + taskUuid + "*");
            if (pendingKeys != null && !pendingKeys.isEmpty()) {
                log.debug("[MinIO批次] taskUuid={} 仍有 {} 张图片在清洗队列，暂不标记 COMPLETED",
                        taskUuid, pendingKeys.size());
                return;
            }

            // 1.5 DB 兜底：检查该批次是否还有 clean_status=0 的记录（防止 img key 时序空洞）
            Long zhimouPending = zhimouImageDetectionMapper.selectCount(
                    new LambdaQueryWrapper<ZhimouImageDetection>()
                            .likeRight(ZhimouImageDetection::getLocalImageName, taskUuid + "/")
                            .eq(ZhimouImageDetection::getDeleted, 0)
                            .eq(ZhimouImageDetection::getCleanStatus, 0));
            Long selfPending = selfModelImageDetectionMapper.selectCount(
                    new LambdaQueryWrapper<SelfModelImageDetection>()
                            .likeRight(SelfModelImageDetection::getLocalImageName, taskUuid + "/")
                            .eq(SelfModelImageDetection::getDeleted, 0)
                            .eq(SelfModelImageDetection::getCleanStatus, 0));
            if ((zhimouPending != null && zhimouPending > 0) || (selfPending != null && selfPending > 0)) {
                log.debug("[MinIO批次] taskUuid={} DB仍有未清洗记录 zhimou={}, self={}, 暂不标记 COMPLETED",
                        taskUuid, zhimouPending, selfPending);
                return;
            }

            // 2. 标记批次 COMPLETED 永久
            String batchKey = "minio:flight:" + taskUuid;
            String prev = stringRedisTemplate.opsForValue().get(batchKey);
            if ("COMPLETED".equals(prev)) {
                return; // 之前已标过
            }
            // 先 delete 清除原有 TTL（PROCESSING 时设置的 1800s），再 set 不带 TTL = 永久
            stringRedisTemplate.delete(batchKey);
            stringRedisTemplate.opsForValue().set(batchKey, "COMPLETED");
            log.info("[MinIO批次] ✅ 标记 COMPLETED taskUuid={}", taskUuid);

            // 3. 删除临时下载目录
            if (minioProperties.isCleanupTempAfter()) {
                String tempDir = minioProperties.getTempDir();
                if (StrUtil.isNotBlank(tempDir)) {
                    Path p = Paths.get(tempDir, taskUuid);
                    try {
                        if (FileUtil.exist(p.toFile())) {
                            boolean del = FileUtil.del(p.toFile());
                            log.info("[MinIO批次] 删除临时下载目录 {} taskUuid={}, result={}",
                                    p.toAbsolutePath(), taskUuid, del);
                        }
                    } catch (Exception e) {
                        log.warn("[MinIO批次] 删除临时目录失败 taskUuid={}, path={}", taskUuid, p, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[MinIO批次] tryCompleteMinioBatch 异常 taskUuid={}", taskUuid, e);
        }
    }

    // ================================================================
    // 内部方法
    // ================================================================

    /**
     * 从知眸 targets_json 提取每个目标的中心点 [x, y]
     * 知眸回调结构: [{rule_id, rule_name, hit_count, targets:[{target_id, class_id, class, confidence, color, x, y, description}]}]
     */
    private List<double[]> extractZhimouCenters(String targetsJson) {
        List<double[]> centers = new ArrayList<>();
        if (StrUtil.isBlank(targetsJson)) return centers;
        try {
            // targetsJson 是 apps[] 原文: [{app_id, app_name, image, objects:[{class_id,name,color,x,y,track_id,confidence,description}]}]
            JSONArray apps = JSONUtil.parseArray(targetsJson);
            for (Object appObj : apps) {
                JSONObject app = (JSONObject) appObj;
                JSONArray objects = app.getJSONArray("objects");
                if (objects == null) continue;
                for (Object obj : objects) {
                    JSONObject o = (JSONObject) obj;
                    // 知眸 bbox {x, y} 含义待实测确认, 当前先按"中心点"处理
                    Double x = o.getDouble("x");
                    Double y = o.getDouble("y");
                    if (x != null && y != null) {
                        centers.add(new double[]{x, y});
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[清洗] 解析知眸 targetsJson 失败: {}", e.getMessage());
        }
        return centers;
    }

    /**
     * 从自有模型 targets_json 提取每个目标的中心点 [cx, cy]
     * 自有模型响应: detections[] = [{id, class_id, class_name, category, confidence, bbox:{x1,y1,x2,y2}}]
     */
    private List<double[]> extractSelfModelCenters(String targetsJson) {
        List<double[]> centers = new ArrayList<>();
        if (StrUtil.isBlank(targetsJson)) return centers;
        try {
            JSONArray detections = JSONUtil.parseArray(targetsJson);
            for (Object detObj : detections) {
                JSONObject det = (JSONObject) detObj;
                JSONObject bbox = det.getJSONObject("bbox");
                if (bbox == null) continue;
                Double x1 = bbox.getDouble("x1");
                Double y1 = bbox.getDouble("y1");
                Double x2 = bbox.getDouble("x2");
                Double y2 = bbox.getDouble("y2");
                if (x1 != null && y1 != null && x2 != null && y2 != null) {
                    double cx = (x1 + x2) / 2.0;
                    double cy = (y1 + y2) / 2.0;
                    centers.add(new double[]{cx, cy});
                }
            }
        } catch (Exception e) {
            log.warn("[清洗] 解析自有模型 targetsJson 失败: {}", e.getMessage());
        }
        return centers;
    }

    /**
     * 批量更新两表的 clean_status
     */
    private void markCleanStatus(List<ZhimouImageDetection> zRecs, List<SelfModelImageDetection> sRecs,
                                 int cleanStatus, String remark) {
        LocalDateTime now = LocalDateTime.now();
        for (ZhimouImageDetection z : zRecs) {
            z.setCleanStatus(cleanStatus);
            z.setCleanTime(now);
            z.setCleanRemark(remark);
            zhimouImageDetectionMapper.updateById(z);
        }
        for (SelfModelImageDetection s : sRecs) {
            s.setCleanStatus(cleanStatus);
            s.setCleanTime(now);
            s.setCleanRemark(remark);
            selfModelImageDetectionMapper.updateById(s);
        }
    }
}
