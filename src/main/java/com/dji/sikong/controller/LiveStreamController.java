package com.dji.sikong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sikong.common.R;
import com.dji.sikong.dto.StartLiveStreamDTO;
import com.dji.sikong.entity.LiveStream;
import com.dji.sikong.service.LiveStreamService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/live-stream")
@CrossOrigin(origins = "*")
public class LiveStreamController {

    @Resource
    private LiveStreamService liveStreamService;

    // ====================================================
    // 需求4：开启旁路推流直播（流媒体地址已在yml中配置，可覆盖）
    // POST /api/live-stream/start-bypass
    // 参数: gatewaySn, aircraftSn, cameraIndex, pushUrl(可选), streamProtocol(rtmp/rtsp等)
    // ====================================================

    @PostMapping("/start-bypass")
    public R<LiveStream> startByPass(@RequestBody @Validated StartLiveStreamDTO dto) {
        try {
            LiveStream live = liveStreamService.startByPassLive(dto);
            if (live == null) {
                return R.fail("该设备不属于默认项目(" + (dto.getProjectUuid() != null ? dto.getProjectUuid() : "yml配置") + "), 已拒绝创建直播(项目级隔离)");
            }
            if (live.getStatus() == 3) {
                return R.fail(live.getStatusMessage() == null ? "直播开启失败" : live.getStatusMessage());
            }
            return R.ok(live);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/stop")
    public R<Boolean> stop(@PathVariable Long id) {
        LiveStream live = liveStreamService.getById(id);
        if (live == null) return R.fail("直播任务不存在");
        live.setStatus(2);
        live.setStopTime(LocalDateTime.now());
        return R.ok(liveStreamService.updateById(live));
    }

    /** 按飞机SN停止当前活跃的旁路推流（飞机降落时自动调用，暴露HTTP供调试） */
    @PostMapping("/stop-by-aircraft")
    public R<LiveStream> stopByAircraftSn(
            @RequestParam String aircraftSn) {
        try {
            LiveStream live = liveStreamService.stopByAircraftSn(aircraftSn);
            if (live == null) return R.fail("该飞机无活跃直播任务: " + aircraftSn);
            return R.ok(live);
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/list")
    public R<List<LiveStream>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String gatewaySn,
            @RequestParam(required = false) String aircraftSn) {
        LambdaQueryWrapper<LiveStream> wrapper = new LambdaQueryWrapper<LiveStream>().eq(LiveStream::getDeleted, 0);
        if (status != null) wrapper.eq(LiveStream::getStatus, status);
        if (gatewaySn != null) wrapper.eq(LiveStream::getGatewaySn, gatewaySn);
        if (aircraftSn != null) wrapper.eq(LiveStream::getAircraftSn, aircraftSn);
        wrapper.orderByDesc(LiveStream::getCreateTime);
        return R.ok(liveStreamService.list(wrapper));
    }

    @GetMapping("/page")
    public R<Page<LiveStream>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<LiveStream> wrapper = new LambdaQueryWrapper<LiveStream>().eq(LiveStream::getDeleted, 0);
        if (status != null) wrapper.eq(LiveStream::getStatus, status);
        if (keyword != null) wrapper.and(w -> w.like(LiveStream::getStreamName, keyword)
                .or().like(LiveStream::getGatewaySn, keyword));
        wrapper.orderByDesc(LiveStream::getCreateTime);
        return R.ok(liveStreamService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @GetMapping("/{id}")
    public R<LiveStream> get(@PathVariable Long id) {
        return R.ok(liveStreamService.getById(id));
    }

    @GetMapping("/streamId/{streamId}")
    public R<LiveStream> getByStreamId(@PathVariable String streamId) {
        return R.ok(liveStreamService.getByStreamId(streamId));
    }
}
