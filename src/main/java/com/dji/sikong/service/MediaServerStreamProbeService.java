package com.dji.sikong.service;

import com.dji.sikong.entity.LiveStream;

import java.util.Collection;

public interface MediaServerStreamProbeService {

    boolean isAircraftStreaming(String aircraftSn);

    /**
     * 按设备SN探测SRS上任意一路可播放的HTTP-FLV流（识别只跟飞机SN绑定，与视频码流编号无关）
     * 候选码流编号优先级: 外部传入(司空码流转发器真实camera,如99-0-91) > live_stream最新记录 > 常见编号兜底 > 裸SN
     * 用于: 同一设备视频码流编号可能变化(如99-0-0被占用改为99-0-91)时,识别只按"该SN有没有流"判断
     *
     * @param deviceSn     设备序列号(飞机或机场)
     * @param extraCameras 额外候选视频码流编号集合(来自司空码流转发器列表),可为null
     * @return 第一个可播放的HTTP-FLV地址(先内网后外网),都不可播放返回null
     */
    String probePlayableUrlByDeviceSn(String deviceSn, Collection<String> extraCameras);

    /**
     * 按设备SN探测可播放HTTP-FLV流的重载: 可指定网络类型
     * @param internal     true=只试内网, false=只试外网
     * @param deviceSn     设备序列号
     * @param extraCameras 额外候选视频码流编号集合,可为null
     */
    String probePlayableUrlByDeviceSn(String deviceSn, Collection<String> extraCameras, boolean internal);

    /**
     * 根据LiveStream构建HTTP-FLV拉流播放地址(优先内网)
     */
    String buildHttpFlvPullUrl(LiveStream liveStream);

    /**
     * 检测指定的HTTP-FLV地址是否能正常播放(返回200且是FLV格式)
     */
    boolean isStreamPlayable(String httpFlvUrl);

    /**
     * 探测可播放的HTTP-FLV地址(先试内网,再试外网),返回第一个可播放的地址,都不可播放返回null
     */
    String probePlayableHttpFlvUrl(LiveStream liveStream);

    /**
     * 按指定网络类型探测可播放的HTTP-FLV地址
     * @param internal true=内网, false=外网
     */
    String probePlayableHttpFlvUrl(LiveStream liveStream, boolean internal);

    /**
     * 构建AI识别流HTTP-FLV拉流地址
     * @param internal true=内网, false=外网
     */
    String buildAiRecognitionPullUrl(LiveStream liveStream, boolean internal);
}
