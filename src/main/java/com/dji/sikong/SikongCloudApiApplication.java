package com.dji.sikong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.dji.sikong.mapper")
public class SikongCloudApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SikongCloudApiApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  大疆司空2 + 上云API 整合后台启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
