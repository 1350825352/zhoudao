package com.dji.sikong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dji.openapi")
public class DjiOpenApiProperties {

    private String baseUrl = "https://es-flight-api-cn.djigate.com";
    private String userToken;
    private String language = "zh";
    private Integer defaultPageSize = 50;

    private String defaultOrganizationUuid;
    private String defaultProjectUuid;
}
