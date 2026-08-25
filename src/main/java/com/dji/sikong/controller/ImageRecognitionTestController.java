package com.dji.sikong.controller;

import cn.hutool.core.util.StrUtil;
import com.dji.sikong.common.R;
import com.dji.sikong.service.ImageCleanupService;
import com.dji.sikong.service.SelfModelDetectService;
import com.dji.sikong.service.ZhimouTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;

/**
 * 图片识别测试入口 (二期新增 - 本地测试用)
 * <p>
 * 在 MinIO 事件接收接口准备好之前, 用这个 Controller 手动触发全流程:
 * 1. 扫描指定目录下的图片
 * 2. 调用知眸图片任务(创建+批量上报)
 * 3. 调用自有模型同步识别
 * 4. 触发数据清洗
 * <p>
 * MinIO 准备好后, 替换为 POST /api/minio/notify 即可, 下游链路完全复用.
 */
@Slf4j
@RestController
@RequestMapping("/test/image-recognition")
public class ImageRecognitionTestController {

    /** 默认测试图片目录(项目根目录下) */
    private static final String DEFAULT_IMAGE_DIR = "testimage";
    /** 默认图片落盘基目录(项目根目录下) */
    private static final String DEFAULT_IMAGE_BASE_DIR = "zhimou-images";
    /** 本地测试设备号(占位SN) */
    private static final String DEFAULT_AIRCRAFT_SN = "LOCAL-TEST-SN";

    @Resource
    private ZhimouTaskService zhimouTaskService;

    @Resource
    private SelfModelDetectService selfModelDetectService;

    @Resource
    private ImageCleanupService imageCleanupService;

    /** 二期新增: 数据源开关服务 */
    @Resource
    private com.dji.sikong.service.TaskSwitchService taskSwitchService;

    /**
     * 触发全流程: 扫描图片 → 知眸图片任务 + 自有模型识别
     * <p>
     * POST /test/image-recognition/run
     * body: {
     *   "imageDir": "testimage",                    // 可选, 默认 testimage
     *   "imageBaseDir": "zhimou-images",            // 可选, 默认 zhimou-images
     *   "aircraftSn": "LOCAL-TEST-SN",              // 可选, 默认 LOCAL-TEST-SN
     *   "appId": "napvhsbecn",                      // 必填, 知眸应用ID(逗号分隔多app)
     *   "applicationPackageId": "466149736e0",      // 必填, 知眸应用包ID
     *   "threshold": 0.5,                          // 可选, 默认0.5
     *   "runZhimou": true,                          // 可选, 是否跑知眸, 默认true
     *   "runSelfModel": true                       // 可选, 是否跑自有模型, 默认true
     * }
     */
    @PostMapping("/run")
    public R run(@RequestBody(required = false) Map<String, Object> params) {
        params = params != null ? params : new HashMap<>();
        String imageDir = StrUtil.blankToDefault((String) params.get("imageDir"), DEFAULT_IMAGE_DIR);
        String imageBaseDir = StrUtil.blankToDefault((String) params.get("imageBaseDir"), DEFAULT_IMAGE_BASE_DIR);
        String aircraftSn = StrUtil.blankToDefault((String) params.get("aircraftSn"), DEFAULT_AIRCRAFT_SN);
        String appId = (String) params.get("appId");
        String applicationPackageId = (String) params.get("applicationPackageId");
        BigDecimal threshold = parseBigDecimal(params.get("threshold"), new BigDecimal("0.5"));
        boolean runZhimou = params.get("runZhimou") == null || Boolean.parseBoolean(params.get("runZhimou").toString());
        boolean runSelfModel = params.get("runSelfModel") == null || Boolean.parseBoolean(params.get("runSelfModel").toString());

        log.info("[图片识别测试] 开始 imageDir={}, imageBaseDir={}, aircraftSn={}, appId={}, runZhimou={}, runSelfModel={}",
                imageDir, imageBaseDir, aircraftSn, appId, runZhimou, runSelfModel);

        // ⭐ 二期新增: 检查图片识别任务开关
        if (!taskSwitchService.isImageTaskEnabled()) {
            return R.fail("图片识别任务已关闭, 请在 admin 换数据源页面开启 image_task 开关");
        }

        // 1. 扫描图片目录
        File dir = new File(imageDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), imageDir);
        }
        if (!dir.exists() || !dir.isDirectory()) {
            return R.fail("图片目录不存在: " + dir.getAbsolutePath());
        }
        File[] imageFiles = dir.listFiles(f -> {
            if (!f.isFile()) return false;
            String name = f.getName().toLowerCase();
            return name.endsWith(".jpeg") || name.endsWith(".jpg") || name.endsWith(".png");
        });
        if (imageFiles == null || imageFiles.length == 0) {
            return R.fail("目录中无图片: " + dir.getAbsolutePath());
        }
        Arrays.sort(imageFiles, Comparator.comparing(File::getName));
        log.info("[图片识别测试] 扫到 {} 张图片, dir={}", imageFiles.length, dir.getAbsolutePath());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imageCount", imageFiles.length);
        result.put("imageDir", dir.getAbsolutePath());

        // ⭐ 并行触发: 知眸(GPU远端) 与 自有模型(CPU本地) 硬件互不冲突, 无数据依赖, 同时开跑
        //    仅最后的数据清洗需要两边结果都到齐, 由前端收到知眸回调后再触发
        //    用 final 数组承接子线程结果(线程内赋值, 主线程返回时读取)
        final String[] taskHolder = new String[1];
        final Exception[] zhimouErrHolder = new Exception[1];
        final int[] selfModelStat = new int[2]; // [success, fail]

        Thread zhimouThread = null;
        Thread selfModelThread = null;

        // 2. 知眸图片任务(异步回调模式) - 新线程并行执行
        if (runZhimou) {
            if (StrUtil.isBlank(appId) || StrUtil.isBlank(applicationPackageId)) {
                return R.fail("跑知眸必须传 appId 和 applicationPackageId");
            }
            log.info("[并行-知眸] >>> 主线程准备启动知眸线程, 图片数={}, ts={}",
                    imageFiles.length, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
            zhimouThread = new Thread(() -> {
                log.info("[并行-知眸] === 知眸线程开始执行 ts={}",
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
                try {
                    taskHolder[0] = zhimouTaskService.createAndReportImageBatch(
                            Arrays.asList(imageFiles), aircraftSn, appId, applicationPackageId, imageBaseDir);
                    log.info("[并行-知眸] === 知眸线程结束(批量上报完成) taskId={}, ts={}",
                            taskHolder[0], java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
                } catch (Exception e) {
                    zhimouErrHolder[0] = e;
                    log.error("[并行-知眸] === 知眸线程异常退出 ts={}",
                            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")), e);
                }
            }, "zhimou-image-batch");
            zhimouThread.start();
            log.info("[并行-知眸] <<< 主线程已启动知眸线程, alive={}, ts={}",
                    zhimouThread.isAlive(), java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        }

        // 3. 自有模型(同步调用) - 新线程并行执行, 每张间隔 0.5 秒
        if (runSelfModel) {
            log.info("[并行-自有] >>> 主线程准备启动自有模型线程, 图片数={}, ts={}",
                    imageFiles.length, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
            selfModelThread = new Thread(() -> {
                log.info("[并行-自有] === 自有模型线程开始执行 ts={}",
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
                int success = 0, fail = 0;
                int total = imageFiles.length;
                for (int i = 0; i < imageFiles.length; i++) {
                    File imgFile = imageFiles[i];
                    log.info("[并行-自有] --- 处理第 {}/{} 张 file={}, ts={}",
                            i + 1, total, imgFile.getName(),
                            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
                    try {
                        selfModelDetectService.detectAndSave(imgFile, threshold, imageBaseDir);
                        success++;
                    } catch (Exception e) {
                        fail++;
                        log.error("[并行-自有] --- 第 {}/{} 张识别异常 file={}", i + 1, total, imgFile.getName(), e);
                    }
                    // 0.5 秒间隔, 降低 CPU 瞬时占用
                    try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                }
                selfModelStat[0] = success;
                selfModelStat[1] = fail;
                log.info("[并行-自有] === 自有模型线程结束 success={}, fail={}, ts={}",
                        success, fail, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
            }, "self-model-detect");
            selfModelThread.start();
            log.info("[并行-自有] <<< 主线程已启动自有模型线程, alive={}, ts={}",
                    selfModelThread.isAlive(), java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        }

        // 等两个子线程都跑完(上报完成即可, 不等知眸回调), 保证 result 里能拿到两边统计
        log.info("[并行-主] 等待子线程结束(仅等上报完成, 不等知眸回调) zhimou={}, selfModel={}, ts={}",
                zhimouThread != null, selfModelThread != null,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        try {
            if (zhimouThread != null) zhimouThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (selfModelThread != null) selfModelThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[并行-主] 两个子线程均结束, 汇总结果, ts={}",
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));

        // 汇总两边结果
        if (taskHolder[0] != null) result.put("zhimouTaskId", taskHolder[0]);
        if (zhimouErrHolder[0] != null) result.put("zhimouError", zhimouErrHolder[0].getMessage());
        if (runSelfModel) {
            result.put("selfModelSuccess", selfModelStat[0]);
            result.put("selfModelFail", selfModelStat[1]);
        }

        // 4. 清洗 (建议前端收到知眸回调后再触发, 这里仅做提示)
        result.put("cleanupHint", "知眸与自有模型已并行触发, 待知眸回调到齐后调用 /test/image-recognition/cleanup 触发清洗");

        return R.ok(result);
    }

    /**
     * 清洗单张图片
     * POST /test/image-recognition/cleanup?localImageName=DJI_20260806165701_0001_V
     */
    @PostMapping("/cleanup")
    public R cleanupOne(@RequestParam("localImageName") String localImageName,
                        @RequestParam(value = "threshold", required = false) Double threshold) {
        String result;
        if (threshold != null) {
            result = imageCleanupService.cleanupSingleImage(localImageName, threshold);
        } else {
            result = imageCleanupService.cleanupSingleImage(localImageName);
        }
        return R.ok(result);
    }

    /**
     * 清洗所有未清洗的图片
     * POST /test/image-recognition/cleanup-all
     */
    @PostMapping("/cleanup-all")
    public R cleanupAll() {
        int cleaned = imageCleanupService.cleanupAllPending();
        return R.ok(cleaned);
    }

    /**
     * 关闭知眸图片任务(测试完成后清理)
     * POST /test/image-recognition/close?taskId=tsk_xxx
     */
    @PostMapping("/close")
    public R closeTask(@RequestParam("taskId") String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return R.fail("taskId不能为空");
        }
        Object r = zhimouTaskService.deleteTask(taskId);
        return R.ok(r);
    }

    private BigDecimal parseBigDecimal(Object val, BigDecimal def) {
        if (val == null) return def;
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return def;
        }
    }
}
