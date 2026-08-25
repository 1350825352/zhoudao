package com.dji.sikong.service;

import cn.hutool.http.HttpRequest;
import com.dji.sikong.common.ImageExifUtil;
import com.dji.sikong.entity.SelfModelImageDetection;
import com.dji.sikong.mapper.SelfModelImageDetectionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 自有模型图片识别服务 (二期新增)
 * <p>
 * 同步调用自有模型识别接口 (POST http://192.168.2.200:5173/api/v1/detect),
 * 解析响应并入库到 self_model_image_detection 表.
 * <p>
 * 与知眸图片识别完全独立, 通过 local_image_name 关联做数据清洗.
 */
@Slf4j
@Service
public class SelfModelDetectService {

    /** 自有模型服务器地址 (后续可改为配置项) */
    private static final String SERVER_URL = "http://192.168.2.200:5173";
    private static final String DETECT_PATH = "/api/v1/detect";
    private static final int TIMEOUT_MS = 5000;

    private static final ObjectMapper JACKSON = new ObjectMapper();

    @Resource
    private SelfModelImageDetectionMapper selfModelImageDetectionMapper;

    /**
     * 同步调用自有模型识别并入库
     *
     * @param imageFile        图片文件
     * @param threshold        置信度阈值 (null用0.5)
     * @param imageBaseDir     图片落盘基目录 (与知眸侧用同一路径)
     * @return 入库后的实体 (含数据库id); 调用失败也入库但call_status=2
     */
    public SelfModelImageDetection detectAndSave(File imageFile, BigDecimal threshold, String imageBaseDir) {
        SelfModelImageDetection rec = new SelfModelImageDetection();
        rec.setServerUrl(SERVER_URL);
        rec.setCallStatus(0);
        rec.setCleanStatus(0);
        rec.setCallTime(LocalDateTime.now());

        // 1. 落盘 + local_image_name + EXIF GPS
        try {
            String fileName = imageFile.getName();
            int dotIdx = fileName.lastIndexOf('.');
            String localImageName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;
            String ext = dotIdx > 0 ? fileName.substring(dotIdx + 1).toLowerCase() : "jpg";
            rec.setLocalImageName(localImageName);
            rec.setImageFormat(ext);

            // 落盘到 imageBaseDir/yyyyMMdd/{原文件名}
            String dayDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            Path targetDir = Paths.get(imageBaseDir, dayDir);
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(fileName);
            if (!targetPath.toFile().exists()) {
                Files.copy(imageFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            rec.setLocalImagePath(dayDir + "/" + fileName);

            // 提取 EXIF GPS
            BigDecimal[] gps = ImageExifUtil.extractGps(imageFile);
            rec.setLatitude(gps[0]);
            rec.setLongitude(gps[1]);
            rec.setAltitude(gps[2]);

            // 图片宽高
            try (java.io.InputStream is = Files.newInputStream(imageFile.toPath())) {
                java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(is);
                if (bi != null) {
                    rec.setImageWidth(bi.getWidth());
                    rec.setImageHeight(bi.getHeight());
                }
            } catch (Exception ignore) {
            }
        } catch (Exception e) {
            log.error("[自有模型] 准备图片元数据失败 file={}", imageFile.getName(), e);
        }

        // 2. 调用 /api/v1/detect (multipart/form-data)
        BigDecimal thr = threshold != null ? threshold : new BigDecimal("0.5");
        rec.setThreshold(thr);
        String fullUrl = SERVER_URL + DETECT_PATH;
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> form = new HashMap<>();
            form.put("image", imageFile);
            form.put("threshold", thr.toPlainString());
            if (rec.getImageWidth() != null) form.put("width", String.valueOf(rec.getImageWidth()));
            if (rec.getImageHeight() != null) form.put("height", String.valueOf(rec.getImageHeight()));

            String respBody = HttpRequest.post(fullUrl)
                    .timeout(TIMEOUT_MS)
                    .form(form)
                    .execute()
                    .body();
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[自有模型] 调用完成 fileName={}, 耗时={}ms, respLen={}",
                    imageFile.getName(), elapsed, respBody != null ? respBody.length() : 0);

            // 3. 解析响应
            JsonNode root = JACKSON.readTree(respBody);
            rec.setRawResponseJson(respBody);
            int code = root.path("code").asInt(-1);
            String message = root.path("message").asText(null);
            if (code != 0) {
                rec.setCallStatus(2);
                rec.setErrorMessage("code=" + code + ", msg=" + message);
                log.warn("[自有模型] 调用失败 code={}, message={}", code, message);
            } else {
                rec.setCallStatus(1);
                JsonNode data = root.path("data");
                rec.setRequestId(data.path("request_id").asText(null));
                rec.setModelVersion(data.path("model_version").asText(null));
                rec.setModelDevice(data.path("model_device").asText(null));
                if (data.has("image_path")) {
                    rec.setServerImagePath(data.path("image_path").asText(null));
                }
                // 处理时间
                if (data.has("processing_time_ms")) {
                    rec.setProcessingTimeMs(data.path("processing_time_ms").asInt());
                }
                if (data.has("processed_at")) {
                    rec.setProcessedAt(data.path("processed_at").asText(null));
                }
                // 识别结果
                JsonNode detections = data.path("detections");
                int hit = (detections != null && detections.isArray()) ? detections.size() : 0;
                rec.setHitCount(hit);
                if (detections != null) {
                    rec.setTargetsJson(detections.toString());
                }
                log.info("[自有模型] 识别成功 fileName={}, reqId={}, model={}, hit={}",
                        imageFile.getName(), rec.getRequestId(), rec.getModelVersion(), hit);
            }
        } catch (Exception e) {
            rec.setCallStatus(2);
            rec.setErrorMessage("调用异常: " + e.getMessage());
            log.error("[自有模型] 调用异常 fileName={}", imageFile.getName(), e);
        }

        // 4. 入库 self_model_image_detection
        selfModelImageDetectionMapper.insert(rec);
        log.info("[自有模型] 入库 id={}, localImageName={}, callStatus={}, hit={}",
                rec.getId(), rec.getLocalImageName(), rec.getCallStatus(), rec.getHitCount());

        return rec;
    }
}
