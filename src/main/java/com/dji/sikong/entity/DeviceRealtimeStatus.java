package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("device_realtime_status")
public class DeviceRealtimeStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceSn;

    private String gatewaySn;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private BigDecimal height;

    private BigDecimal altitude;

    private BigDecimal elevation;

    private BigDecimal verticalSpeed;

    private BigDecimal horizontalSpeed;

    private BigDecimal homeLatitude;

    private BigDecimal homeLongitude;

    private BigDecimal distanceToHome;

    private Integer deviceMode;

    private String flightStatus;

    private Integer droneInDock;

    private Integer coverState;

    private Integer batteryPercent;

    private BigDecimal batteryTemperature;

    private Integer batteryVoltage;

    private Integer batteryCurrent;

    private Integer chargeState;

    private Integer gpsSatelliteCount;

    private Integer rtkSatelliteCount;

    private Integer positionQuality;

    private Integer rtkFixed;

    private Integer calibrationState;

    private BigDecimal windSpeed;

    private BigDecimal environmentTemperature;

    private Integer humidity;

    private Integer rainfall;

    private Integer networkType;

    private Integer networkQuality;

    private Integer sdrLinkState;

    private Integer sdrQuality;

    @TableField("`4g_link_state`")
    private Integer link4gState;

    @TableField("`4g_quality`")
    private Integer quality4g;

    private String currentTaskId;

    private String currentTaskName;

    @TableField("wayline_uuid")
    private String waylineUuid;

    private Integer flightStepCode;

    private Long storageTotal;

    private Long storageUsed;

    private String osdRawJson;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
