package com.dji.sikong.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AircraftVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String aircraftSn;
    private String aircraftName;
    private String aircraftModel;
    private String modelKey;
    private Integer onlineStatus;
    private Integer pairedStatus;
    private Integer droneInDock;

    private List<AircraftCameraVO> cameras;
}
