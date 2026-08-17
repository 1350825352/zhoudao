package com.dji.sikong.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AircraftCameraVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cameraIndex;
    private String cameraModel;
    private Integer cameraType;
    private String lensType;
    private String pixel;
    private String focalLength;
}
