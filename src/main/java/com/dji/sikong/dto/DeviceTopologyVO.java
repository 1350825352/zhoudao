package com.dji.sikong.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DeviceTopologyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String gatewaySn;
    private String gatewayName;
    private Integer gatewayType;
    private String gatewayModel;
    private Integer onlineStatus;

    private List<AircraftVO> aircrafts;
}
