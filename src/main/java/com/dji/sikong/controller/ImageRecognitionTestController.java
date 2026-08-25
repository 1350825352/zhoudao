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

        // 2. 跑知眸图片任务(异步回调模式)
        String taskId = null;
        if (runZhimou) {
            if (StrUtil.isBlank(appId) || StrUtil.isBlank(applicationPackageId)) {
                return R.fail("跑知眸必须传 appId 和 applicationPackageId");
            }
            try {
                taskId = zhimouTaskService.createAndReportImageBatch(
                        Arrays.asList(imageFiles), aircraftSn, appId, applicationPackageId, imageBaseDir);
                result.put("zhimouTaskId", taskId);
                log.info("[图片识别测试] 知眸批量上报完成 taskId={}", taskId);
            } catch (Exception e) {
                log.error("[图片识别测试] 知眸批量上报异常", e);
                result.put("zhimouError", e.getMessage());
            }
        }

        // 3. 跑自有模型(同步调用)
        if (runSelfModel) {
            int success = 0, fail = 0;
            for (File imgFile : imageFiles) {
                try {
                    selfModelDetectService.detectAndSave(imgFile, threshold, imageBaseDir);
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.error("[图片识别测试] 自有模型调用异常 file={}", imgFile.getName(), e);
                }
            }
            result.put("selfModelSuccess", success);
            result.put("selfModelFail", fail);
        }

        // 4. 清洗 (建议前端收到知眸回调后再触发, 这里仅做提示)
        result.put("cleanupHint", "知眸异步回调约28秒/张, 待回调到齐后调用 /test/image-recognition/cleanup 触发清洗");

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
