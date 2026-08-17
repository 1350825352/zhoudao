package com.dji.sikong.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class ExecuteFlightTaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotBlank(message = "机场SN不能为空")
    private String gatewaySn;

    @NotBlank(message = "航线UUID不能为空")
    private String waylineUuid;

    private String aircraftSn;

    private Integer rthAltitude = 120;

    private String rthMode = "preset";

    private String outOfControlActionInFlight = "return_home";

    private String waylinePrecisionType = "rtk";

    private String resumableStatus = "manual";

    private String repeatType = "nonrepeating";

    private String projectUuid;

    private String operator;
}