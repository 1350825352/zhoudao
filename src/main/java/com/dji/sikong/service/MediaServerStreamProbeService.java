package com.dji.sikong.service;

import com.dji.sikong.entity.LiveStream;

public interface MediaServerStreamProbeService {

    boolean isAircraftStreaming(String aircraftSn);

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
