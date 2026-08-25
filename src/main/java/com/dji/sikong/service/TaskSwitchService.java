package com.dji.sikong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.entity.TaskSwitchConfig;
import com.dji.sikong.mapper.TaskSwitchConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 数据源开关服务
 * 提供 Redis 缓存 + 数据库持久化
 * 高频检查(每秒调度)走 Redis, 修改时同步更新 DB + Redis
 */
@Slf4j
@Service
public class TaskSwitchService extends ServiceImpl<TaskSwitchConfigMapper, TaskSwitchConfig> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_PREFIX = "switch:";
    private static final long CACHE_TTL_HOURS = 24 * 7;  // 7天, 足够长

    /** 开关键名常量 */
    public static final String KEY_VIDEO_TASK = "video_task";
    public static final String KEY_IMAGE_TASK = "image_task";
    public static final String KEY_ALERT_DATA_SOURCE = "alert_data_source";

    /** 警情数据源可选值 */
    public static final String DS_VIDEO = "video";
    public static final String DS_IMAGE = "image";

    /**
     * 查询所有开关状态(给前端展示)
     */
    public Map<String, String> listAll() {
        Map<String, String> result = new LinkedHashMap<>();
        List<TaskSwitchConfig> list = list();
        for (TaskSwitchConfig c : list) {
            result.put(c.getSwitchKey(), c.getValue());
        }
        return result;
    }

    /**
     * 读取开关值(优先 Redis, 未命中查 DB 并回填)
     */
    public String getValue(String key) {
        String redisKey = REDIS_PREFIX + key;
        String cached = stringRedisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return cached;
        }
        // 未命中: 查 DB
        TaskSwitchConfig config = getByKey(key);
        if (config == null) {
            return null;
        }
        // 回填 Redis
        stringRedisTemplate.opsForValue().set(redisKey, config.getValue(), CACHE_TTL_HOURS, TimeUnit.HOURS);
        return config.getValue();
    }

    /**
     * 修改开关值(更新 DB + Redis)
     */
    public boolean setValue(String key, String value, String updater) {
        TaskSwitchConfig config = getByKey(key);
        if (config == null) {
            log.warn("[开关] 不存在的 key={}, 跳过修改", key);
            return false;
        }
        // 更新 DB
        config.setValue(value);
        config.setUpdateTime(LocalDateTime.now());
        config.setUpdater(updater != null ? updater : "unknown");
        boolean ok = updateById(config);
        if (!ok) {
            log.error("[开关] 更新数据库失败 key={} value={}", key, value);
            return false;
        }
        // 更新 Redis
        stringRedisTemplate.opsForValue().set(REDIS_PREFIX + key, value, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.info("[开关] 修改成功 key={} value={} updater={}", key, value, updater);
        return true;
    }

    /** 视频任务是否开启 */
    public boolean isVideoTaskEnabled() {
        return "1".equals(getValue(KEY_VIDEO_TASK));
    }

    /** 图片识别任务是否开启 */
    public boolean isImageTaskEnabled() {
        return "1".equals(getValue(KEY_IMAGE_TASK));
    }

    /** 警情数据源: "video" 或 "image" */
    public String getAlertDataSource() {
        String v = getValue(KEY_ALERT_DATA_SOURCE);
        return v != null ? v : DS_VIDEO;
    }

    private TaskSwitchConfig getByKey(String key) {
        LambdaQueryWrapper<TaskSwitchConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(TaskSwitchConfig::getSwitchKey, key);
        return getOne(qw);
    }
}
