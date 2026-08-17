package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wayline")
public class Wayline implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String waylineUuid;

    private String waylineName;

    private String projectUuid;

    private String projectName;

    private Integer waylineType;

    private Integer templateType;

    private String wpmlObjectKey;

    private String wpmlDownloadUrl;

    private BigDecimal totalDistance;

    private Integer estimatedTime;

    private Integer waypointCount;

    private BigDecimal height;

    private BigDecimal speed;

    private Integer actionCount;

    private Integer syncSource;

    private LocalDateTime lastSyncTime;

    private String rawJson;

    private String remark;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
