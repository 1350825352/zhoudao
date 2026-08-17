package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("live_stream")
public class LiveStream implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String streamId;

    private String streamName;

    private String gatewaySn;

    private String aircraftSn;

    private String cameraIndex;

    private Integer pushType;

    private String streamProtocol;

    private String qualityType;

    private Integer videoExpire;

    private String pushUrl;

    private String pullUrl;

    private String urlType;

    private Integer status;

    private String statusMessage;

    private LocalDateTime startTime;

    private LocalDateTime stopTime;

    private LocalDateTime expireTime;

    private String requestJson;

    private String responseJson;

    // ================ 知眸AI任务关联字段 ================
    /** 知眸任务ID(对应 POST /api/task 返回的data.task_id) */
    private String zhimouTaskId;
    /** 知眸前端选的应用ID(创建任务必填) */
    private String zhimouAppId;
    /** 知眸应用包ID(前端先选应用包) */
    private String zhimouPkgId;
    /** 知眸任务状态: 0-未创建 1-运行中 2-已成功关闭 3-创建失败 4-调用关闭失败 */
    private Integer zhimouStatus;
    private java.time.LocalDateTime zhimouStartTime;
    private java.time.LocalDateTime zhimouStopTime;
    /** 知眸最后一次错误信息 */
    private String zhimouLastError;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
