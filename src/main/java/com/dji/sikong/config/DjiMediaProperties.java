package com.dji.sikong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dji.media")
public class DjiMediaProperties {

    private StreamServer streamServer = new StreamServer();

    @Data
    public static class StreamServer {
        private String rtmpUrl = "rtmp://127.0.0.1/live";
        private String pullUrlTemplate;
        private String rtmpUrlInternal;
        private String pullUrlTemplateInternal;
        private String rtmpUrlAi;
        private String pullUrlTemplateAi;
        private String type = "srs";
    }
}
