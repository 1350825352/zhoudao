package com.dji.sikong.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阶段3：MinIO 对象存储连接配置
 * 对应 application-company-a.yml 中 minio:* 段
 *
 * 真实生效路径：fh_sync/{ORG_UUID}/{PROJECT_UUID}/media/{TASK_UUID}/{FILENAME}
 *   ORG_UUID     = dji.openapi.defaultOrganizationUuid
 *   PROJECT_UUID = dji.openapi.defaultProjectUuid
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务地址，如 http://192.168.2.200:9000  (注意不要带 /bucket 后缀) */
    private String endpoint;

    /** MinIO Access Key (账户) */
    private String accessKey;

    /** MinIO Secret Key (密码) */
    private String secretKey;

    /** 存储桶名，真实环境 flydata */
    private String bucket = "flydata";

    /** 扫描前缀（仅轮询）。默认 fh_sync/，对应司空2同步的目录结构 */
    private String scanPrefix = "fh_sync/";

    /** 轮询间隔毫秒，默认 30s */
    private long pollIntervalMs = 30000L;

    /** MinIO -> 本地临时下载目录。空则自动使用 {zhimou.image.saveRootDir}/minio-incoming */
    private String tempDir;

    /** 批处理完成后是否删除 tempDir/{TASK_UUID}，默认 true */
    private boolean cleanupTempAfter = true;

    /**
     * 处理前是否校验上传完整性：
     *   folder_info.expected_file_count == uploaded_file_count
     *   AND media 接口数量 == MinIO 实际图片数量
     * 默认 true（建议开启，防止处理上传到一半的批次）
     */
    private boolean validateUploadCountBeforeProcess = true;

    /** 批次 PROCESSING 锁 TTL，单位秒，默认 1800 (30分钟) */
    private int batchLockTtlSec = 1800;

    /** 图片级 Redis 状态 TTL，单位天，默认 7 天（超过自动丢弃避免孤儿 key 堆积） */
    private int imageStatusTtlDays = 7;
}
