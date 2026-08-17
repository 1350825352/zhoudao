package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("work_order")
public class WorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Long alarmId;

    private String alarmType;

    private String aircraftSn;

    private String aircraftName;

    private String waylineUuid;

    private String waylineName;

    private String alarmImagePath;

    private String handlerEmail;

    private String handlerName;

    private String status;

    private String handleComment;

    private String rejectReason;

    private String ccEmail;

    private String h5Token;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private LocalDateTime eventTime;

    private LocalDateTime submitTime;

    private LocalDateTime completeTime;

    private LocalDateTime rejectTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}