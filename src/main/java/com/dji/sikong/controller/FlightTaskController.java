package com.dji.sikong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sikong.common.R;
import com.dji.sikong.dto.ExecuteFlightTaskDTO;
import com.dji.sikong.entity.FlightTask;
import com.dji.sikong.service.FlightTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/flight-task")
@CrossOrigin(origins = "*")
public class FlightTaskController {

    @Resource
    private FlightTaskService flightTaskService;

    // ====================================================
    // 需求3：执行航线任务 POST /api/flight-task/execute
    // 参数: aircraftName(飞机名称/别名), taskName(任务名称), waylineUuid, executeType(1=司空2OpenAPI, 2=MQTT上云API)
    // ====================================================

    @PostMapping("/execute")
    public R<FlightTask> executeTask(@RequestBody @Validated ExecuteFlightTaskDTO dto) {
        System.out.println("[执行任务-DEBUG] 收到请求: taskName=" + dto.getTaskName() + " gatewaySn=" + dto.getGatewaySn() + " waylineUuid=" + dto.getWaylineUuid() + " rthAltitude=" + dto.getRthAltitude());
        try {
            FlightTask task = flightTaskService.executeTask(dto);
            System.out.println("[执行任务-结果] taskStatus=" + task.getTaskStatus() + " statusMessage=" + task.getStatusMessage());
            if (task.getTaskStatus() == 6) {
                return R.fail(task.getStatusMessage() == null ? "任务下发失败" : task.getStatusMessage());
            }
            return R.ok(task);
        } catch (Exception e) {
            System.out.println("[执行任务-异常] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return R.fail(e.getMessage());
        }
    }

    // ====================================================
    // 任务CRUD
    // ====================================================

    @GetMapping("/list")
    public R<List<FlightTask>> list(
            @RequestParam(required = false) Integer taskStatus,
            @RequestParam(required = false) String aircraftSn) {
        LambdaQueryWrapper<FlightTask> wrapper = new LambdaQueryWrapper<FlightTask>().eq(FlightTask::getDeleted, 0);
        if (taskStatus != null) wrapper.eq(FlightTask::getTaskStatus, taskStatus);
        if (aircraftSn != null) wrapper.eq(FlightTask::getAircraftSn, aircraftSn);
        wrapper.orderByDesc(FlightTask::getCreateTime);
        return R.ok(flightTaskService.list(wrapper));
    }

    @GetMapping("/page")
    public R<Page<FlightTask>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer taskStatus,
            @RequestParam(required = false) String aircraftSn,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<FlightTask> wrapper = new LambdaQueryWrapper<FlightTask>().eq(FlightTask::getDeleted, 0);
        if (taskStatus != null) wrapper.eq(FlightTask::getTaskStatus, taskStatus);
        if (aircraftSn != null) wrapper.eq(FlightTask::getAircraftSn, aircraftSn);
        if (keyword != null) wrapper.and(w -> w.like(FlightTask::getTaskName, keyword)
                .or().like(FlightTask::getAircraftName, keyword));
        wrapper.orderByDesc(FlightTask::getCreateTime);
        return R.ok(flightTaskService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @GetMapping("/{id}")
    public R<FlightTask> get(@PathVariable Long id) {
        return R.ok(flightTaskService.getById(id));
    }

    @GetMapping("/uuid/{uuid}")
    public R<FlightTask> getByTaskUuid(@PathVariable String uuid) {
        return R.ok(flightTaskService.getByTaskUuid(uuid));
    }

    @PostMapping("/{id}/pause")
    public R<Boolean> pause(@PathVariable Long id) {
        FlightTask task = flightTaskService.getById(id);
        if (task == null) return R.fail("任务不存在");
        task.setTaskStatus(4);
        task.setStatusMessage("前端请求暂停");
        return R.ok(flightTaskService.updateById(task));
    }

    @PostMapping("/{id}/cancel")
    public R<Boolean> cancel(@PathVariable Long id) {
        FlightTask task = flightTaskService.getById(id);
        if (task == null) return R.fail("任务不存在");
        task.setTaskStatus(5);
        task.setStatusMessage("前端请求取消");
        return R.ok(flightTaskService.updateById(task));
    }
}
