package com.dji.sikong.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DeviceRealtimeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String deviceSn;
    private String deviceName;
    private String deviceModel;
    private Integer deviceType;
    private String gatewaySn;

    private Integer onlineStatus;

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

    private String flightStatus;
    private Integer deviceMode;
    private Integer droneInDock;

    private Integer batteryPercent;
    private BigDecimal batteryTemperature;

    private BigDecimal windSpeed;
    private BigDecimal environmentTemperature;
    private Integer humidity;
    private Integer rainfall;

    private Integer gpsSatelliteCount;
    private Integer rtkSatelliteCount;
    private Integer rtkFixed;

    private String updateTime;

    private List<AircraftCameraVO> cameras;
}
