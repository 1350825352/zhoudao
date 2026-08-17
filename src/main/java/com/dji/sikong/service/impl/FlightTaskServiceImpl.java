package com.dji.sikong.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dji.sikong.common.RedisKeyConstants;
import com.dji.sikong.config.DjiOpenApiProperties;
import com.dji.sikong.dto.ExecuteFlightTaskDTO;
import com.dji.sikong.entity.Device;
import com.dji.sikong.entity.FlightTask;
import com.dji.sikong.entity.Wayline;
import com.dji.sikong.mapper.FlightTaskMapper;
import com.dji.sikong.mqtt.DjiMqttClient;
import com.dji.sikong.openapi.DjiOpenApiClient;
import com.dji.sikong.service.DeviceService;
import com.dji.sikong.service.FlightTaskService;
import com.dji.sikong.service.WaylineService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FlightTaskServiceImpl extends ServiceImpl<FlightTaskMapper, FlightTask> implements FlightTaskService {

    @Resource
    private FlightTaskMapper flightTaskMapper;

    @Resource
    private DeviceService deviceService;

    @Resource
    private WaylineService waylineService;

    @Resource
    private DjiOpenApiClient openApiClient;

    @Resource
    private DjiMqttClient mqttClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DjiOpenApiProperties openApiProperties;

    @Override
    public FlightTask executeTask(ExecuteFlightTaskDTO dto) {
        Device gateway = deviceService.getBySn(dto.getGatewaySn());
        if (gateway == null) {
            throw new RuntimeException("未找到机场：" + dto.getGatewaySn());
        }

        Device aircraft = null;
        if (StrUtil.isNotBlank(dto.getAircraftSn())) {
            aircraft = deviceService.getBySn(dto.getAircraftSn());
        } else {
            aircraft = deviceService.lambdaQuery()
                    .eq(Device::getGatewaySn, dto.getGatewaySn())
                    .eq(Device::getDeleted, 0)
                    .one();
        }

        Wayline wayline = waylineService.getByUuid(dto.getWaylineUuid());
        if (wayline == null) {
            throw new RuntimeException("未找到航线：" + dto.getWaylineUuid());
        }

        String projectUuid = dto.getProjectUuid();
        if (StrUtil.isBlank(projectUuid) && wayline != null) projectUuid = wayline.getProjectUuid();
        if (StrUtil.isBlank(projectUuid)) projectUuid = openApiProperties.getDefaultProjectUuid();

        FlightTask task = new FlightTask();
        task.setTaskName(dto.getTaskName());
        task.setProjectUuid(projectUuid);
        task.setWaylineUuid(dto.getWaylineUuid());
        task.setWaylineName(wayline.getWaylineName());
        task.setAircraftSn(aircraft != null ? aircraft.getSn() : dto.getGatewaySn());
        task.setAircraftName(aircraft != null && aircraft.getDeviceName() != null ? aircraft.getDeviceName() : (aircraft != null ? aircraft.getSn() : dto.getGatewaySn()));
        task.setGatewaySn(dto.getGatewaySn());
        task.setTaskStatus(0);
        task.setProgress(0);
        task.setOperator(dto.getOperator());
        task.setExecuteType(1);
        task.setPlanStartTime(LocalDateTime.now());
        flightTaskMapper.insert(task);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", dto.getTaskName());
            body.put("sn", dto.getGatewaySn());
            body.put("wayline_uuid", dto.getWaylineUuid());
            body.put("time_zone", "Asia/Shanghai");
            body.put("task_type", "immediate");
            body.put("rth_altitude", dto.getRthAltitude() != null ? dto.getRthAltitude() : 120);
            body.put("rth_mode", StrUtil.isNotBlank(dto.getRthMode()) ? dto.getRthMode() : "preset");
            body.put("out_of_control_action_in_flight", StrUtil.isNotBlank(dto.getOutOfControlActionInFlight()) ? dto.getOutOfControlActionInFlight() : "return_home");
            body.put("wayline_precision_type", StrUtil.isNotBlank(dto.getWaylinePrecisionType()) ? dto.getWaylinePrecisionType() : "rtk");
            body.put("resumable_status", StrUtil.isNotBlank(dto.getResumableStatus()) ? dto.getResumableStatus() : "manual");
            body.put("repeat_type", StrUtil.isNotBlank(dto.getRepeatType()) ? dto.getRepeatType() : "nonrepeating");

            JsonNode resp = openApiClient.createFlightTask(projectUuid, body);
            task.setRequestJson(JSONUtil.toJsonStr(body));

            if (resp != null) {
                task.setResponseJson(resp.toString());
                if (resp.has("data")) {
                    JsonNode data = resp.get("data");
                    if (data.has("task_uuid")) {
                        task.setTaskUuid(data.get("task_uuid").asText());
                    }
                }
                if (resp.has("code") && resp.get("code").asInt() == 0) {
                    task.setTaskStatus(1);
                    task.setStatusMessage("任务已下发至司空2");
                    task.setActualStartTime(LocalDateTime.now());
                } else {
                    task.setTaskStatus(6);
                    task.setStatusMessage("下发失败: " + (resp.has("msg") ? resp.get("msg").asText() : "未知错误"));
                }
            } else {
                task.setTaskStatus(6);
                task.setStatusMessage("司空2 OpenAPI无响应");
            }
        } catch (Exception e) {
            task.setTaskStatus(6);
            task.setStatusMessage("异常: " + e.getMessage());
        }
        flightTaskMapper.updateById(task);
        return task;
    }

    @Override
    public FlightTask getByTaskUuid(String taskUuid) {
        if (StrUtil.isBlank(taskUuid)) return null;
        return flightTaskMapper.selectOne(
                new LambdaQueryWrapper<FlightTask>()
                        .and(w -> w.eq(FlightTask::getTaskUuid, taskUuid).or().eq(FlightTask::getId, Long.parseLong(taskUuid)))
                        .eq(FlightTask::getDeleted, 0));
    }
}