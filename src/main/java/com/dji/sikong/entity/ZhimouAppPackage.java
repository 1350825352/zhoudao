package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("zhimou_app_package")
public class ZhimouAppPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("zapk_id")
    private String zapkId;

    private String name;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("downloaded_at")
    private LocalDateTime downloadedAt;

    @TableField("deleted")
    private Integer deleted;

    @TableField("sync_time")
    private LocalDateTime syncTime;
}