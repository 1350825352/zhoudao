package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sn;

    private String deviceName;

    private Integer deviceType;

    private String deviceModel;

    private String modelKey;

    private String gatewaySn;

    private String orgUuid;

    private String projectUuid;

    private Integer onlineStatus;

    private Integer pairedStatus;

    private String firmwareVersion;

    private LocalDateTime lastOnlineTime;

    private LocalDateTime lastOfflineTime;

    private String bindCode;

    private String remark;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
