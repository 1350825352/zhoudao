package com.dji.sikong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sikong.common.DeviceTypeConstants;
import com.dji.sikong.common.R;
import com.dji.sikong.config.DjiConfig;
import com.dji.sikong.entity.Device;
import com.dji.sikong.entity.Wayline;
import com.dji.sikong.entity.WorkOrder;
import com.dji.sikong.entity.ZhimouDetectionResult;
import com.dji.sikong.mapper.DeviceMapper;
import com.dji.sikong.mapper.WaylineMapper;
import com.dji.sikong.mapper.WorkOrderMapper;
import com.dji.sikong.mapper.ZhimouDetectionResultMapper;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Resource
    private ZhimouDetectionResultMapper detectionResultMapper;

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private WaylineMapper waylineMapper;

    @Resource
    private DjiConfig djiConfig;

    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        String projectUuid = djiConfig.getDefaultProjectUuid();

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        long alarmCount = detectionResultMapper.selectCount(
                new LambdaQueryWrapper<ZhimouDetectionResult>()
                        .ge(ZhimouDetectionResult::getEventTime, todayStart)
                        .le(ZhimouDetectionResult::getEventTime, todayEnd)
                        .eq(ZhimouDetectionResult::getDeleted, 0));

        long pendingCount = workOrderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getStatus, "PENDING")
                        .eq(WorkOrder::getDeleted, 0));

        LambdaQueryWrapper<Device> deviceWrapper = new LambdaQueryWrapper<Device>()
                .eq(Device::getOnlineStatus, 1)
                .eq(Device::getDeleted, 0)
                .in(Device::getDeviceType, DeviceTypeConstants.GATEWAY_DOCK, DeviceTypeConstants.GATEWAY_REMOTE, DeviceTypeConstants.AIRCRAFT)
                .eq(StrUtil.isNotBlank(projectUuid), Device::getProjectUuid, projectUuid);
        long deviceCount = deviceMapper.selectCount(deviceWrapper);

        LambdaQueryWrapper<Wayline> waylineWrapper = new LambdaQueryWrapper<Wayline>()
                .eq(Wayline::getDeleted, 0);
        if (projectUuid != null && !projectUuid.isEmpty()) {
            waylineWrapper.eq(Wayline::getProjectUuid, projectUuid);
        }
        long routeCount = waylineMapper.selectCount(waylineWrapper);

        stats.put("alarmCount", alarmCount);
        stats.put("pendingCount", pendingCount);
        stats.put("deviceCount", deviceCount);
        stats.put("routeCount", routeCount);

        return R.ok(stats);
    }
}
