package com.dji.sikong.controller;

import com.dji.sikong.common.R;
import com.dji.sikong.service.MinioPollingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 阶段3：MinIO 轮询运维接口
 * <p>
 * 仅用于运维/调试：
 *   - 查看当前所有批次状态（PROCESSING/COMPLETED）
 *   - 手动清理 Redis 锁并重跑某批次（某批次 PROCESSING TTL 30min 内不想等、
 *     或自动流程出现异常需回滚时）
 * <p>
 * 正式流程全部走 @Scheduled scanAndProcess，不需要任何人工介入。
 */
@Slf4j
@RestController
@RequestMapping("/minio")
public class MinioController {

    @Resource
    private MinioPollingService minioPollingService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * GET /api/minio/debug
     * 临时诊断：查看所有 minio:flight:img:* 图片级 Redis key 及其 Hash 字段
     */
    @GetMapping("/debug")
    public R<Object> debug() {
        Set<String> imgKeys = stringRedisTemplate.keys("minio:flight:img:*");
        List<Map<String, Object>> result = new ArrayList<>();
        if (imgKeys != null) {
            for (String k : imgKeys) {
                Map<Object, Object> fields = stringRedisTemplate.opsForHash().entries(k);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("key", k);
                entry.put("fields", fields);
                entry.put("ttl", stringRedisTemplate.getExpire(k));
                result.add(entry);
            }
        }
        // 同时返回批次级 key
        Set<String> batchKeys = stringRedisTemplate.keys("minio:flight:*");
        Map<String, Object> batchStates = new LinkedHashMap<>();
        if (batchKeys != null) {
            for (String k : batchKeys) {
                if (k.startsWith("minio:flight:img:")) continue;
                batchStates.put(k, stringRedisTemplate.opsForValue().get(k));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imgKeys", result);
        out.put("batchStates", batchStates);
        return R.ok(out);
    }

    /**
     * GET /api/minio/batches
     * 查询所有 minio:flight:* 批次状态（Redis String 值：PROCESSING / COMPLETED）
     */
    @GetMapping("/batches")
    public R<Map<String, String>> listBatches() {
        return R.ok(minioPollingService.listBatchStates());
    }

    /**
     * POST /api/minio/retry/{taskUuid}
     * 手动重跑某飞行任务图片批次：
     *   1. 删除 Redis 批次状态锁 minio:flight:{taskUuid}
     *   2. 删除该批次所有图片级状态 key minio:flight:img:{taskUuid}*
     *   3. 立即同步调用 scan 结果中找该批次并 processOneGroup
     * （若扫不到，下一轮 @Scheduled 会自然处理）
     */
    @PostMapping("/retry/{taskUuid}")
    public R<String> retry(@PathVariable("taskUuid") String taskUuid) {
        log.info("[MinIO运维] 收到手动重试 taskUuid={}", taskUuid);
        boolean ok = minioPollingService.retryBatch(taskUuid);
        return ok ? R.ok("已提交重试，请查看日志 / 30s 后 GET /api/minio/batches 观察状态")
                  : R.fail("重试失败，taskUuid 为空或 processOneGroup 抛异常（查服务端ERROR日志）");
    }

    /**
     * POST /api/minio/scan-now
     * 立即触发一次 scanAndProcess 轮询（调试用），不等 30s 定时。
     */
    @PostMapping("/scan-now")
    public R<String> scanNow() {
        log.info("[MinIO运维] 收到立即扫描请求 scan-now");
        minioPollingService.scanAndProcess();
        return R.ok("立即扫描已触发，请查看服务端日志或 GET /api/minio/batches");
    }
}
