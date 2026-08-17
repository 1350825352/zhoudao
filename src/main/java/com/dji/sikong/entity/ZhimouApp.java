package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("zhimou_app")
public class ZhimouApp {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("app_id")
    private String appId;

    @TableField("zapk_id")
    private String zapkId;

    private String name;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("license_expire")
    private Long licenseExpire;

    private Integer perpetual;

    @TableField("deleted")
    private Integer deleted;

    @TableField("sync_time")
    private LocalDateTime syncTime;
}