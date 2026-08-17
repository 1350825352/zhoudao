package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("work_order_image")
public class WorkOrderImage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workOrderId;

    private String imagePath;

    private String imageType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}