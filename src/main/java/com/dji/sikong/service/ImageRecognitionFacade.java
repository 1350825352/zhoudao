package com.dji.sikong.service;

import cn.hutool.core.util.StrUtil;
import com.dji.sikong.service.facade.BatchProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 阶段3新增：图片识别统一入口 Facade（从 ImageRecognitionTestController.run 提炼）
 * <p>
 * 阶段2手动测试入口 /test/image-recognition/run 和 阶段3 MinioPollingService
 * 都调用这个 Service 方法，保证两条链路完全一致的并行行为。
 */
@Slf4j
@Service
public class ImageRecognitionFacade {

    @Resource
    private ZhimouTaskService zhimouTaskService;

    @Resource
    private SelfModelDetectService selfModelDetectService;

    /**
     * 并行批处理一批图片（知眸GPU线程 + 自有模型CPU线程，join后返回）
     *
     * @param imageFiles               待处理图片列表（必须已存在本地磁盘）
     * @param aircraftSn               飞机SN；阶段3传 detail API 返回的真实 drone_sn，阶段2传 LOCAL-TEST-SN
     * @param appId                    知眸应用ID(逗号分隔多app)
     * @param applicationPackageId     知眸应用包ID
     * @param threshold                自有模型阈值
     * @param imageBaseDir             知眸/自有模型落盘保存根目录（zhimou.image.save-root-dir）
     * @param customLocalImagePrefix   **阶段3专用**: 非空时所有 local_image_name 统一加 "{prefix}/" 前缀，
     *                                 用于区分不同架次 (传 taskUuid 值)；阶段2手动入口传 null 沿用旧规则（文件去扩展名）
     * @param runZhimou                是否开启知眸
     * @param runSelfModel             是否开启自有模型
     * @param zhimouReportIntervalMs   知眸 reportImage 之间间隔，默认 1000ms
     * @param selfModelIntervalMs      自有模型每张之间间隔，默认 500ms
     */
    public BatchProcessResult processImageBatch(List<File> imageFiles,
                                                String aircraftSn,
                                                String appId,
                                                String applicationPackageId,
                                                BigDecimal threshold,
                                                String imageBaseDir,
                                                String customLocalImagePrefix,
                                                boolean runZhimou,
                                                boolean runSelfModel,
                                                long zhimouReportIntervalMs,
                                                long selfModelIntervalMs) {
        // ===== 基本校验 =====
        if (imageFiles == null || imageFiles.isEmpty()) {
            return BatchProcessResult.fail("imageFiles 为空");
        }
        if (runZhimou && (StrUtil.isBlank(appId) || StrUtil.isBlank(applicationPackageId))) {
            return BatchProcessResult.fail("runZhimou=true 时必须传 appId 和 applicationPackageId");
        }
        if (StrUtil.isBlank(aircraftSn)) {
            return BatchProcessResult.fail("aircraftSn 不能为空(阶段3从 detail API 取 drone_sn; 阶段2手动传 LOCAL-TEST-SN)");
        }
        if (StrUtil.isBlank(imageBaseDir)) {
            return BatchProcessResult.fail("imageBaseDir 不能为空");
        }
        BigDecimal actualThreshold = threshold != null ? threshold : new BigDecimal("0.5");

        // 按文件名排序（保证 imageId 顺序稳定，便于调试对比）
        imageFiles.sort(Comparator.comparing(File::getName));
        File[] arr = imageFiles.toArray(new File[0]);

        BatchProcessResult result = new BatchProcessResult();
        result.setImageCount(arr.length);

        final String[] taskHolder = new String[1];
        final Exception[] zhimouErrHolder = new Exception[1];
        final int[] selfModelStat = new int[2]; // [success, fail]

        Thread zhimouThread = null;
        Thread selfModelThread = null;

        // ===== 线程A：知眸图片任务（GPU远端异步） =====
        if (runZhimou) {
            zhimouThread = new Thread(() -> {
                try {
                    // 阶段3带前缀 -> 调重载；阶段2(null) -> 走旧 createAndReportImageBatch(5参数)
                    if (StrUtil.isNotBlank(customLocalImagePrefix)) {
                        taskHolder[0] = zhimouTaskService.createAndReportImageBatch(
                                Arrays.asList(arr), aircraftSn, appId, applicationPackageId, imageBaseDir,
                                customLocalImagePrefix, zhimouReportIntervalMs);
                    } else {
                        taskHolder[0] = zhimouTaskService.createAndReportImageBatch(
                                Arrays.asList(arr), aircraftSn, appId, applicationPackageId, imageBaseDir);
                    }
                } catch (Exception e) {
                    zhimouErrHolder[0] = e;
                    log.error("[知眸批处理] 异常 aircraftSn={}, appId={}", aircraftSn, appId, e);
                }
            }, "zhimou-image-batch");
            zhimouThread.start();
        }

        // ===== 线程B：自有模型识别（CPU本地同步） =====
        if (runSelfModel) {
            selfModelThread = new Thread(() -> {
                int success = 0, fail = 0;
                for (int i = 0; i < arr.length; i++) {
                    File imgFile = arr[i];
                    try {
                        if (StrUtil.isNotBlank(customLocalImagePrefix)) {
                            // 阶段3：构造带前缀的 customLocalImageName = {TASK_UUID}/文件名去扩展名
                            String baseName = imgFile.getName();
                            int dotIdx = baseName.lastIndexOf('.');
                            String baseWithoutExt = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
                            String customName = customLocalImagePrefix + "/" + baseWithoutExt;
                            selfModelDetectService.detectAndSave(imgFile, actualThreshold, imageBaseDir, customName);
                        } else {
                            // 阶段2：走旧 detectAndSave(3参数) 不带 prefix
                            selfModelDetectService.detectAndSave(imgFile, actualThreshold, imageBaseDir);
                        }
                        success++;
                    } catch (Exception e) {
                        fail++;
                        log.error("[自有模型批处理] 第 {}/{} 张识别异常 file={}", i + 1, arr.length, imgFile.getName(), e);
                    }
                    // 每张间隔
                    if (selfModelIntervalMs > 0 && i < arr.length - 1) {
                        try { Thread.sleep(selfModelIntervalMs); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                selfModelStat[0] = success;
                selfModelStat[1] = fail;
            }, "self-model-detect");
            selfModelThread.start();
        }

        // ===== join =====
        try { if (zhimouThread != null) zhimouThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { if (selfModelThread != null) selfModelThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // ===== 汇总 =====
        result.setZhimouTaskId(taskHolder[0]);
        result.setZhimouError(zhimouErrHolder[0] != null ? zhimouErrHolder[0].getMessage() : null);
        result.setSelfModelSuccess(selfModelStat[0]);
        result.setSelfModelFail(selfModelStat[1]);
        return result;
    }
}
