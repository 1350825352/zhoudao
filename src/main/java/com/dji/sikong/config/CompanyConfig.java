package com.dji.sikong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "company")
public class CompanyConfig {

    private String name = "默认公司";
}
