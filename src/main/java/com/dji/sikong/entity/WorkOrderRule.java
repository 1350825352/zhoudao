package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalTime;
import java.time.LocalDateTime;

@Data
@TableName("work_order_rule")
public class WorkOrderRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String alarmType;

    private LocalTime startTime;

    private LocalTime endTime;

    private String aircraftSn;

    private Long handlerUserId;

    private String handlerName;

    private String handlerEmail;

    private Integer sortOrder;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}