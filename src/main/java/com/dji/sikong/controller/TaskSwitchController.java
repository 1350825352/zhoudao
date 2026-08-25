package com.dji.sikong.controller;

import com.dji.sikong.common.R;
import com.dji.sikong.service.TaskSwitchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源开关管理接口
 * admin-v3 "换数据源" 页面调用
 */
@Slf4j
@RestController
@RequestMapping("/task-switch")
public class TaskSwitchController {

    @Resource
    private TaskSwitchService taskSwitchService;

    /** 查询所有开关状态 */
    @GetMapping
    public R<Map<String, String>> list() {
        return R.ok(taskSwitchService.listAll());
    }

    /**
     * 修改开关状态
     * @param key   开关键名: video_task / image_task / alert_data_source
     * @param value 开关值: 0/1 或 video/image
     */
    @PutMapping("/{key}")
    public R<?> update(@PathVariable String key,
                       @RequestParam String value,
                       @RequestParam(required = false) String updater) {
        // 参数校验
        if (!isValidKey(key)) {
            return R.fail("无效的开关名: " + key);
        }
        if (!isValidValue(key, value)) {
            return R.fail("无效的开关值: " + value);
        }
        boolean ok = taskSwitchService.setValue(key, value, updater);
        if (!ok) {
            return R.fail("修改失败");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", key);
        data.put("value", value);
        return R.ok(data);
    }

    private boolean isValidKey(String key) {
        return TaskSwitchService.KEY_VIDEO_TASK.equals(key)
                || TaskSwitchService.KEY_IMAGE_TASK.equals(key)
                || TaskSwitchService.KEY_ALERT_DATA_SOURCE.equals(key);
    }

    private boolean isValidValue(String key, String value) {
        if (TaskSwitchService.KEY_ALERT_DATA_SOURCE.equals(key)) {
            return TaskSwitchService.DS_VIDEO.equals(value) || TaskSwitchService.DS_IMAGE.equals(value);
        }
        // video_task / image_task: 只接受 0/1
        return "0".equals(value) || "1".equals(value);
    }
}
