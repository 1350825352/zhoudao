package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知眸AI识别任务表(对应 zhimou_task)
 */
@Data
@TableName("zhimou_task")
public class ZhimouTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long liveStreamId;

    /** 知眸平台返回的任务ID(唯一) */
    private String zhimouTaskId;

    private String gatewaySn;

    private String aircraftSn;

    private String cameraIndex;

    /** 前端选的应用ID(创建任务必填) */
    private String appId;

    /** 前端选的应用包ID */
    private String applicationPackageId;

    /** 我方业务ID: 一般传 live_stream.id */
    private String businessId;

    /** 任务模式: video / image */
    private String mode;

    /** 传给知眸的输入视频流地址(SRS RTMP) */
    private String inputUrl;

    /** 知眸AI叠加标注后的输出流地址(可空) */
    private String outputUrl;

    /** 回调地址(等于application.yml里的callback-url) */
    private String reportUrl;

    /**
     * 状态:
     * 0-待创建 1-运行中 2-已关闭 3-创建失败 4-关闭失败 5-知眸自动关闭(超时)
     */
    private Integer status;

    private String statusMessage;

    private LocalDateTime startTime;

    private LocalDateTime stopTime;

    /** 创建任务时发送给知眸的完整JSON */
    private String requestJson;

    /** 知眸返回的创建任务响应JSON */
    private String responseJson;

    /** 航线UUID(一次飞行任务期间固定不变) */
    @TableField("wayline_uuid")
    private String waylineUuid;

    private String lastError;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
