package com.dji.sikong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "dji.mqtt")
public class DjiMqttProperties {

    private String host;
    private String username;
    private String password;
    private String clientId;
    private Integer connectionTimeout = 30;
    private Integer keepAlive = 60;
    private Boolean cleanSession = true;
    private Boolean automaticReconnect = true;
    private List<String> subscribeTopics;
}
