package com.dji.sikong.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dji.sikong.entity.ZhimouImageDetection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 识别数据推送服务
 * 清洗完成后异步POST到外部接口, 支持重试3次, 内网快速重试
 */
@Service
@EnableAsync
@Slf4j
public class PushService {

    @Value("${push.enabled:false}")
    private boolean enabled;

    @Value("${push.url:}")
    private String pushUrl;

    @Value("${push.timeout:3000}")
    private int timeout;

    @Value("${push.max-retries:3}")
    private int maxRetries;

    @Value("${push.confirm-mode:code0}")
    private String confirmMode;

    @Value("${h5.host:192.168.2.130}")
    private String serverHost;

    @Value("${server.port:8080}")
    private int serverPort;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "push-retry");
        t.setDaemon(true);
        return t;
    });

    private static final int[] RETRY_DELAYS_MS = {3000, 5000};

    /**
     * 异步推送单条识别结果 (清洗后触发, 只推 clean_status=1)
     */
    @Async
    public void pushAlarmResult(ZhimouImageDetection z) {
        if (!enabled || StrUtil.isBlank(pushUrl)) {
            return;
        }
        if (z.getCleanStatus() == null || z.getCleanStatus() != 1) {
            return;
        }

        Map<String, Object> payload = buildPayload(z);
        String json = JSONUtil.toJsonStr(payload);
        log.info("[推送] 开始推送 id={}, sn={}, image={}", z.getId(), z.getDeviceId(), z.getLocalImageName());

        doPushWithRetry(json, z.getId(), 1);
    }

    private void doPushWithRetry(String json, Long recordId, int attempt) {
        try {
            String resp = cn.hutool.http.HttpUtil.createPost(pushUrl)
                    .timeout(timeout)
                    .body(json)
                    .contentType("application/json")
                    .execute()
                    .body();

            if (isConfirmed(resp)) {
                log.info("[推送] 成✅ id={}, attempt={}, resp={}", recordId, attempt, resp);
                return;
            }

            log.warn("[推送] 对方未确认 id={}, attempt={}, resp={}", recordId, attempt, resp);
            scheduleRetry(json, recordId, attempt);
        } catch (Exception e) {
            log.warn("[推送] 请求失败 id={}, attempt={}, err={}", recordId, attempt, e.getMessage());
            scheduleRetry(json, recordId, attempt);
        }
    }

    private void scheduleRetry(String json, Long recordId, int attempt) {
        if (attempt >= maxRetries) {
            log.error("[推送] 最终失败❌ id={}, 共尝试{}次, payload={}", recordId, maxRetries, json);
            return;
        }

        int delayMs = attempt - 1 < RETRY_DELAYS_MS.length ? RETRY_DELAYS_MS[attempt - 1] : 5000;
        int nextAttempt = attempt + 1;
        log.info("[推送] {}秒后重试 id={}, attempt={}", delayMs / 1000, recordId, nextAttempt);

        scheduler.schedule(() -> doPushWithRetry(json, recordId, nextAttempt),
                delayMs, TimeUnit.MILLISECONDS);
    }

    private boolean isConfirmed(String resp) {
        if (StrUtil.isBlank(resp)) {
            return "http200".equals(confirmMode);
        }
        if ("http200".equals(confirmMode)) {
            return true;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(resp);
            Integer code = obj.getInt("code");
            return code != null && code == 0;
        } catch (Exception e) {
            return "http200".equals(confirmMode);
        }
    }

    private Map<String, Object> buildPayload(ZhimouImageDetection z) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aircraft_sn", z.getDeviceId());
        payload.put("task_id", z.getZhimouTaskId());
        payload.put("image_name", z.getLocalImageName());
        payload.put("latitude", z.getLatitude());
        payload.put("longitude", z.getLongitude());
        payload.put("altitude", z.getAltitude());
        payload.put("app_name", z.getAppName());
        payload.put("hit_count", z.getHitCount());
        payload.put("clean_status", z.getCleanStatus());
        payload.put("clean_remark", z.getCleanRemark());

        // 标注图URL
        String annotatedUrl = null;
        if (StrUtil.isNotBlank(z.getAnnotatedLocalPath())) {
            annotatedUrl = "http://" + serverHost + ":" + serverPort + "/api/image-detect-img/" + z.getAnnotatedLocalPath();
        }
        payload.put("annotated_image_url", annotatedUrl);

        // 时间格式化为字符串(hutool默认会把LocalDateTime序列化成毫秒时间戳, 父系统按文档解析字符串会报错)
        payload.put("detect_time", z.getCreateTime() == null ? null
                : z.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return payload;
    }
}
