package com.dji.sikong.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 阶段3：MinIO 客户端配置
 * 启动时校验 bucket 是否存在（不抛出异常，仅 ERROR 日志，避免阻断服务启动）。
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Resource
    private MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        if (properties.getEndpoint() == null || properties.getEndpoint().trim().isEmpty()) {
            log.warn("[MinIO] endpoint 未配置，跳过 MinioClient 初始化（阶段3功能不可用）");
            return null;
        }

        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();

        // 启动时健康检查（bucketExists）
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            if (exists) {
                log.info("[MinIO] 连接成功，endpoint={}, bucket={} ✅",
                        properties.getEndpoint(), properties.getBucket());
            } else {
                log.error("[MinIO] bucket 不存在! endpoint={}, bucket={}，请先到 MinIO 控制台创建",
                        properties.getEndpoint(), properties.getBucket());
            }
        } catch (Exception e) {
            log.error("[MinIO] 健康检查失败, endpoint={}, bucket={}, err={}",
                    properties.getEndpoint(), properties.getBucket(), e.getMessage());
        }

        return client;
    }
}
