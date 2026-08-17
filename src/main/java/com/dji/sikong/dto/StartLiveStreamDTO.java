package com.dji.sikong.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class StartLiveStreamDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "设备SN(网关/飞机)不能为空")
    private String gatewaySn;

    private String aircraftSn;

    private String cameraIndex;

    private String streamName;

    private String streamProtocol = "rtmp";

    private String qualityType = "adaptive";

    private Integer videoExpire = 7200;

    private String pushUrl;

    private String projectUuid;

    // ================== 知眸AI分析相关(前端选好应用后带上,开直播时自动创建AI任务) ==================
    /**
     * 知眸应用ID(创建知眸任务必填),由前端先调用 /zhimou/apps 选择后传入
     * 为空则: 跳过创建知眸任务
     */
    private String zhimouAppId;

    /**
     * 知眸应用包ID(可选)
     */
    private String zhimouPkgId;
}
