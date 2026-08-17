package com.dji.sikong.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dji.sikong.dto.StartLiveStreamDTO;
import com.dji.sikong.entity.LiveStream;

public interface LiveStreamService extends IService<LiveStream> {

    LiveStream startByPassLive(StartLiveStreamDTO dto);

    LiveStream getByStreamId(String streamId);

    LiveStream stopByAircraftSn(String aircraftSn);

    /**
     * 只关闭码流转发器(司空2侧),不更新live_stream.status,不关知眸任务
     * 用于:飞机降落后及时停推流(省资源),但视频播放等到SRS流真断了才停
     */
    boolean stopStreamConverterOnly(String aircraftSn);

    /** 更新live_stream.status=2(直播结束),不碰知眸任务字段 */
    void markLiveStreamStopped(String aircraftSn, String reason);

    boolean hasActiveLive(String aircraftSn);

    /**
     * 【只读不写!】查询飞机是否有活跃直播（先查司空2API，回退本地DB）
     * ⚠️注意⚠️: 此方法绝不修改 live_stream.status（以前因为司空2API状态同步延迟，
     *   PUT auto_push=true成功后立即GET仍返回false，旧代码会把本地status=1误改回2，
     *   导致健康检查误关知眸任务→死循环。 现在已改为只读返回，不碰DB任何字段！）
     */
    boolean syncAndHasActiveLive(String aircraftSn);
}
