package com.dji.sikong.controller;

import com.dji.sikong.common.R;
import com.dji.sikong.mqtt.DjiMqttClient;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/mqtt")
@CrossOrigin(origins = "*")
public class MqttController {

    @Resource
    private DjiMqttClient mqttClient;

    /**
     * 手动发送MQTT消息给设备（调试用）
     */
    @PostMapping("/publish")
    public R<Boolean> publish(@RequestBody Map<String, Object> body) {
        String topic = (String) body.get("topic");
        String payload = body.get("payload") instanceof String
                ? (String) body.get("payload")
                : cn.hutool.json.JSONUtil.toJsonStr(body.get("payload"));
        int qos = body.get("qos") != null ? (Integer) body.get("qos") : 1;
        return R.ok(mqttClient.publish(topic, payload, qos));
    }

    /**
     * 检查MQTT连接状态（调试用）
     */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> map = new HashMap<>();
        map.put("client", mqttClient);
        map.put("timestamp", System.currentTimeMillis());
        return R.ok(map);
    }
}
