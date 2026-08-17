package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("flight_task")
public class FlightTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskUuid;

    private String taskName;

    private String projectUuid;

    private String waylineUuid;

    private String waylineName;

    private String aircraftSn;

    private String aircraftName;

    private String gatewaySn;

    private Integer taskStatus;

    private String statusMessage;

    private Integer stepCode;

    private Integer progress;

    private LocalDateTime planStartTime;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    private String operator;

    private Integer executeType;

    private Integer resultCode;

    private String bid;

    private String requestJson;

    private String responseJson;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
