package com.dji.sikong.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class DatabaseInitConfig {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public DatabaseInitConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        try {
            executeWorkOrderTables();
        } catch (Exception e) {
            log.warn("[数据库初始化] 工单表创建失败，可能已存在: {}", e.getMessage());
        }
        try {
            executeZhimouTaskConfigTable();
        } catch (Exception e) {
            log.warn("[数据库初始化] 知眸任务配置表创建失败，可能已存在: {}", e.getMessage());
        }
        try {
            executeZhimouAppPackageTable();
        } catch (Exception e) {
            log.warn("[数据库初始化] 知眸应用包表创建失败，可能已存在: {}", e.getMessage());
        }
        try {
            executeZhimouAppTable();
        } catch (Exception e) {
            log.warn("[数据库初始化] 知眸应用表创建失败，可能已存在: {}", e.getMessage());
        }
    }

    private void executeWorkOrderTables() throws Exception {
        String workOrderTableSql = 
            "CREATE TABLE IF NOT EXISTS `work_order` (" +
            "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键'," +
            "`code` VARCHAR(32) NOT NULL COMMENT '工单编号'," +
            "`alarm_id` BIGINT UNSIGNED NOT NULL COMMENT '关联告警ID'," +
            "`alarm_type` VARCHAR(100) DEFAULT NULL COMMENT '告警类型'," +
            "`aircraft_sn` VARCHAR(100) DEFAULT NULL COMMENT '飞机SN'," +
            "`aircraft_name` VARCHAR(128) DEFAULT NULL COMMENT '飞机名称'," +
            "`wayline_uuid` VARCHAR(64) DEFAULT NULL COMMENT '航线UUID'," +
            "`wayline_name` VARCHAR(128) DEFAULT NULL COMMENT '航线名称'," +
            "`alarm_image_path` VARCHAR(500) DEFAULT NULL COMMENT '告警图片路径'," +
            "`handler_email` VARCHAR(100) DEFAULT NULL COMMENT '处理人邮箱'," +
            "`handler_name` VARCHAR(50) DEFAULT NULL COMMENT '处理人姓名'," +
            "`status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态'," +
            "`handle_comment` TEXT DEFAULT NULL COMMENT '处理意见'," +
            "`reject_reason` TEXT DEFAULT NULL COMMENT '驳回原因'," +
            "`cc_email` VARCHAR(100) DEFAULT NULL COMMENT '抄送邮箱'," +
            "`h5_token` VARCHAR(64) DEFAULT NULL COMMENT 'H5访问Token'," +
            "`latitude` DECIMAL(12,9) DEFAULT NULL COMMENT '告警位置纬度'," +
            "`longitude` DECIMAL(12,9) DEFAULT NULL COMMENT '告警位置经度'," +
            "`event_time` DATETIME DEFAULT NULL COMMENT '告警时间'," +
            "`submit_time` DATETIME DEFAULT NULL COMMENT '提交时间'," +
            "`complete_time` DATETIME DEFAULT NULL COMMENT '完成时间'," +
            "`reject_time` DATETIME DEFAULT NULL COMMENT '驳回时间'," +
            "`deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'," +
            "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "`update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
            "PRIMARY KEY (`id`)," +
            "UNIQUE KEY `uk_code` (`code`)," +
            "UNIQUE KEY `uk_alarm_id` (`alarm_id`)," +
            "UNIQUE KEY `uk_h5_token` (`h5_token`)," +
            "KEY `idx_status` (`status`)," +
            "KEY `idx_aircraft_sn` (`aircraft_sn`)," +
            "KEY `idx_create_time` (`create_time`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单主表'";

        String workOrderImageTableSql = 
            "CREATE TABLE IF NOT EXISTS `work_order_image` (" +
            "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键'," +
            "`work_order_id` BIGINT UNSIGNED NOT NULL COMMENT '关联工单ID'," +
            "`image_path` VARCHAR(500) NOT NULL COMMENT '图片本地路径'," +
            "`image_type` VARCHAR(20) NOT NULL DEFAULT 'HANDLER' COMMENT '图片类型'," +
            "`create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "PRIMARY KEY (`id`)," +
            "KEY `idx_work_order_id` (`work_order_id`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单图片表'";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.execute(workOrderTableSql);
            log.info("[数据库初始化] work_order 表创建成功");
            
            statement.execute(workOrderImageTableSql);
            log.info("[数据库初始化] work_order_image 表创建成功");
            
        } catch (Exception e) {
            log.error("[数据库初始化] 创建工单表失败", e);
            throw e;
        }
    }

    private void executeZhimouTaskConfigTable() throws Exception {
        String sql = 
            "CREATE TABLE IF NOT EXISTS `zhimou_task_config` (" +
            "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键'," +
            "`zapk_id` VARCHAR(50) DEFAULT NULL COMMENT '应用包ID'," +
            "`app_ids` TEXT COMMENT '应用ID列表(JSON数组格式)'," +
            "`report_url` VARCHAR(255) DEFAULT NULL COMMENT '回调地址'," +
            "`output_url` VARCHAR(255) DEFAULT NULL COMMENT '输出地址'," +
            "`osd_display_name` TINYINT(1) DEFAULT 1 COMMENT '是否显示名称'," +
            "`osd_display_box` TINYINT(1) DEFAULT 1 COMMENT '是否显示框'," +
            "`osd_display_track` TINYINT(1) DEFAULT 1 COMMENT '是否显示轨迹'," +
            "`osd_font_size` INT DEFAULT 0 COMMENT '字体大小'," +
            "`osd_border_size` INT DEFAULT 0 COMMENT '边框大小'," +
            "`deleted` TINYINT(1) DEFAULT 0 COMMENT '删除标记'," +
            "`create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
            "`update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
            "PRIMARY KEY (`id`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸任务配置表'";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.execute(sql);
            log.info("[数据库初始化] zhimou_task_config 表创建成功");
            
        } catch (Exception e) {
            log.error("[数据库初始化] 创建知眸任务配置表失败", e);
            throw e;
        }
    }

    private void executeZhimouAppPackageTable() throws Exception {
        String sql = 
            "CREATE TABLE IF NOT EXISTS `zhimou_app_package` (" +
            "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键'," +
            "`zapk_id` VARCHAR(50) NOT NULL COMMENT '应用包ID'," +
            "`name` VARCHAR(255) DEFAULT NULL COMMENT '应用包名称'," +
            "`created_at` DATETIME DEFAULT NULL COMMENT '创建时间'," +
            "`updated_at` DATETIME DEFAULT NULL COMMENT '更新时间'," +
            "`downloaded_at` DATETIME DEFAULT NULL COMMENT '下载时间'," +
            "`deleted` TINYINT(1) DEFAULT 0 COMMENT '删除标记'," +
            "`sync_time` DATETIME DEFAULT NULL COMMENT '同步时间'," +
            "PRIMARY KEY (`id`)," +
            "UNIQUE KEY `uk_zapk_id` (`zapk_id`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸应用包缓存表'";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.execute(sql);
            log.info("[数据库初始化] zhimou_app_package 表创建成功");
            
        } catch (Exception e) {
            log.error("[数据库初始化] 创建知眸应用包表失败", e);
            throw e;
        }
    }

    private void executeZhimouAppTable() throws Exception {
        String sql = 
            "CREATE TABLE IF NOT EXISTS `zhimou_app` (" +
            "`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键'," +
            "`app_id` VARCHAR(50) NOT NULL COMMENT '应用ID'," +
            "`zapk_id` VARCHAR(50) NOT NULL COMMENT '所属应用包ID'," +
            "`name` VARCHAR(255) DEFAULT NULL COMMENT '应用名称'," +
            "`updated_at` DATETIME DEFAULT NULL COMMENT '更新时间'," +
            "`license_expire` BIGINT DEFAULT 0 COMMENT '许可证过期时间'," +
            "`perpetual` TINYINT(1) DEFAULT 0 COMMENT '是否永久有效'," +
            "`deleted` TINYINT(1) DEFAULT 0 COMMENT '删除标记'," +
            "`sync_time` DATETIME DEFAULT NULL COMMENT '同步时间'," +
            "PRIMARY KEY (`id`)," +
            "UNIQUE KEY `uk_app_id` (`app_id`)," +
            "KEY `idx_zapk_id` (`zapk_id`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸应用缓存表'";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            statement.execute(sql);
            log.info("[数据库初始化] zhimou_app 表创建成功");
            
        } catch (Exception e) {
            log.error("[数据库初始化] 创建知眸应用表失败", e);
            throw e;
        }
    }
}