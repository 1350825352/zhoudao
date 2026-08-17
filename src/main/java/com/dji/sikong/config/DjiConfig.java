package com.dji.sikong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dji.openapi")
public class DjiConfig {

    private String defaultOrganizationUuid;

    private String defaultProjectUuid;
}
