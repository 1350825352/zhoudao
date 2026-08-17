package com.dji.sikong.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 知眸AI相关数据库表/字段自动初始化
 *  - 解决 zhimo_task / zhimou_detection_result 表不存在的问题
 *  - 解决 live_stream 表缺少 zhimou_* 列的问题
 *  - 用 CREATE TABLE IF NOT EXISTS + information_schema 判断列是否存在, 重复执行安全
 */
@Slf4j
@Component
@Order(-1)
public class ZhimouDbAutoInitConfig implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureLiveStreamColumns();
            ensureZhimouTaskTable();
            ensureZhimouDetectionResultTable();
            log.info("[知眸-DB初始化] 完成✅");
        } catch (Exception e) {
            log.warn("[知眸-DB初始化] 出现异常(若表/列已存在可忽略): " + e.getMessage());
        }
    }

    // =========================================================================
    // 1. live_stream 表补齐 zhimou_* 列(若不存在则添加)
    // =========================================================================
    private void ensureLiveStreamColumns() {
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'live_stream'");
        java.util.Set<String> exists = new java.util.HashSet<>();
        for (Map<String, Object> c : cols) exists.add(String.valueOf(c.get("COLUMN_NAME")).toLowerCase());

        addColumnIfMissing(exists, "live_stream", "zhimou_task_id",
                "VARCHAR(128) DEFAULT NULL COMMENT '知眸任务ID' AFTER `response_json`");
        addColumnIfMissing(exists, "live_stream", "zhimou_app_id",
                "VARCHAR(128) DEFAULT NULL COMMENT '知眸应用ID' AFTER `zhimou_task_id`");
        addColumnIfMissing(exists, "live_stream", "zhimou_pkg_id",
                "VARCHAR(128) DEFAULT NULL COMMENT '知眸应用包ID' AFTER `zhimou_app_id`");
        addColumnIfMissing(exists, "live_stream", "zhimou_status",
                "TINYINT NOT NULL DEFAULT 0 COMMENT '知眸任务状态:0未创建1运行中2已关闭3创建失败4关闭失败' AFTER `zhimou_pkg_id`");
        addColumnIfMissing(exists, "live_stream", "zhimou_start_time",
                "DATETIME DEFAULT NULL AFTER `zhimou_status`");
        addColumnIfMissing(exists, "live_stream", "zhimou_stop_time",
                "DATETIME DEFAULT NULL AFTER `zhimou_start_time`");
        addColumnIfMissing(exists, "live_stream", "zhimou_last_error",
                "VARCHAR(512) DEFAULT NULL AFTER `zhimou_stop_time`");

        // 索引(若不存在)
        addIndexIfMissing("live_stream", "idx_zhimou_task_id", "zhimou_task_id");
        addIndexIfMissing("live_stream", "idx_zhimou_status", "zhimou_status");
    }

    private void addColumnIfMissing(java.util.Set<String> exists, String table, String column, String def) {
        if (exists.contains(column.toLowerCase())) return;
        String sql = String.format("ALTER TABLE `%s` ADD COLUMN `%s` %s", table, column, def);
        log.info("[知眸-DB初始化] 新增列: " + column);
        jdbcTemplate.execute(sql);
    }

    private void addIndexIfMissing(String table, String idxName, String column) {
        try {
            List<Map<String, Object>> idxs = jdbcTemplate.queryForList(
                    "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                    table, idxName);
            if (!idxs.isEmpty()) return;
            jdbcTemplate.execute(String.format("ALTER TABLE `%s` ADD KEY `%s` (`%s`)", table, idxName, column));
            log.info("[知眸-DB初始化] 新增索引: " + idxName);
        } catch (Exception e) {
            log.debug("[知眸-DB初始化] 索引" + idxName + "可能已存在: " + e.getMessage());
        }
    }

    // =========================================================================
    // 2. zhimou_task 表 (知眸任务表, 一对一 live_stream)
    //    字段顺序严格匹配 entity/ZhimouTask.java
    // =========================================================================
    private void ensureZhimouTaskTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `zhimou_task` (" +
                "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
                "  `live_stream_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联live_stream.id'," +
                "  `zhimou_task_id` VARCHAR(128) DEFAULT NULL COMMENT '知眸平台返回的任务ID(唯一)'," +
                "  `gateway_sn` VARCHAR(64) DEFAULT NULL COMMENT '机场SN'," +
                "  `aircraft_sn` VARCHAR(64) DEFAULT NULL COMMENT '飞机SN'," +
                "  `camera_index` VARCHAR(32) DEFAULT NULL COMMENT '镜头编号 99-0-0等'," +
                "  `app_id` VARCHAR(128) DEFAULT NULL COMMENT '前端选的应用ID(创建任务必填)'," +
                "  `application_package_id` VARCHAR(128) DEFAULT NULL COMMENT '前端选的应用包ID'," +
                "  `business_id` VARCHAR(128) DEFAULT NULL COMMENT '我方业务ID: 一般传 live_stream.id'," +
                "  `mode` VARCHAR(16) NOT NULL DEFAULT 'video' COMMENT '任务模式: video / image'," +
                "  `input_url` VARCHAR(1024) DEFAULT NULL COMMENT '传给知眸的输入视频流地址(SRS RTMP)'," +
                "  `output_url` VARCHAR(1024) DEFAULT NULL COMMENT '知眸AI叠加标注后的输出流地址(可空)'," +
                "  `report_url` VARCHAR(1024) DEFAULT NULL COMMENT '回调地址(等于application.yml里的callback-url)'," +
                "  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待创建1运行中2已关闭3创建失败4关闭失败5知眸自动关闭超时'," +
                "  `status_message` VARCHAR(512) DEFAULT NULL," +
                "  `start_time` DATETIME DEFAULT NULL," +
                "  `stop_time` DATETIME DEFAULT NULL," +
                "  `request_json` JSON DEFAULT NULL COMMENT '创建任务时发送给知眸的完整JSON'," +
                "  `response_json` JSON DEFAULT NULL COMMENT '知眸返回的创建任务响应JSON'," +
                "  `last_error` VARCHAR(1024) DEFAULT NULL," +
                "  `deleted` TINYINT NOT NULL DEFAULT 0," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_zhimou_task_id` (`zhimou_task_id`)," +
                "  KEY `idx_live_stream_id` (`live_stream_id`)," +
                "  KEY `idx_aircraft_sn` (`aircraft_sn`)," +
                "  KEY `idx_status` (`status`)," +
                "  KEY `idx_create_time` (`create_time`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸AI分析任务表';";
        jdbcTemplate.execute(sql);
        // 若表已存在, 补齐可能缺失的列 (兼容旧库升级)
        ensureZhimouTaskColumns();
        log.info("[知眸-DB初始化] zhimou_task 表 OK");
    }

    private void ensureZhimouTaskColumns() {
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zhimou_task'");
        java.util.Set<String> exists = new java.util.HashSet<>();
        for (Map<String, Object> c : cols) exists.add(String.valueOf(c.get("COLUMN_NAME")).toLowerCase());

        // 补齐缺失列 (仅补已存在的表不会自动补齐的关键列)
        addColumnIfMissing(exists, "zhimou_task", "live_stream_id",
                "BIGINT UNSIGNED DEFAULT NULL COMMENT '关联live_stream.id' AFTER `id`");
        addColumnIfMissing(exists, "zhimou_task", "gateway_sn",
                "VARCHAR(64) DEFAULT NULL COMMENT '机场SN' AFTER `zhimou_task_id`");
        addColumnIfMissing(exists, "zhimou_task", "camera_index",
                "VARCHAR(32) DEFAULT NULL COMMENT '镜头编号' AFTER `aircraft_sn`");
        addColumnIfMissing(exists, "zhimou_task", "application_package_id",
                "VARCHAR(128) DEFAULT NULL COMMENT '应用包ID' AFTER `app_id`");
        addColumnIfMissing(exists, "zhimou_task", "report_url",
                "VARCHAR(1024) DEFAULT NULL COMMENT '回调地址' AFTER `output_url`");
        addColumnIfMissing(exists, "zhimou_task", "status_message",
                "VARCHAR(512) DEFAULT NULL AFTER `status`");
        addColumnIfMissing(exists, "zhimou_task", "wayline_uuid",
                "VARCHAR(100) DEFAULT NULL COMMENT '航线UUID(一次飞行任务期间固定不变)' AFTER `response_json`");
        // request_json / response_json 若原来是 TEXT 需升级为 JSON
        upgradeColumnType("zhimou_task", "request_json", "JSON DEFAULT NULL COMMENT '创建任务请求JSON'");
        upgradeColumnType("zhimou_task", "response_json", "JSON DEFAULT NULL COMMENT '创建任务响应JSON'");
        // 补唯一索引
        addUniqueIndexIfMissing("zhimou_task", "uk_zhimou_task_id", "zhimou_task_id");
    }

    private void upgradeColumnType(String table, String column, String def) {
        try {
            List<Map<String, Object>> rs = jdbcTemplate.queryForList(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    table, column);
            if (rs.isEmpty()) return;
            String oldType = String.valueOf(rs.get(0).get("COLUMN_TYPE")).toLowerCase();
            if (def.toLowerCase().startsWith("json") && !"json".equals(oldType)) {
                jdbcTemplate.execute(String.format("ALTER TABLE `%s` MODIFY COLUMN `%s` %s", table, column, def));
                log.info("[知眸-DB初始化] 升级列类型: " + table + "." + column + " -> " + def.split(" ")[0]);
            }
        } catch (Exception e) {
            log.debug("[知眸-DB初始化] 升级列类型失败(一般不致命): " + column + ", " + e.getMessage());
        }
    }

    private void addUniqueIndexIfMissing(String table, String idxName, String column) {
        try {
            List<Map<String, Object>> idxs = jdbcTemplate.queryForList(
                    "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                    table, idxName);
            if (!idxs.isEmpty()) return;
            jdbcTemplate.execute(String.format("ALTER TABLE `%s` ADD UNIQUE KEY `%s` (`%s`)", table, idxName, column));
            log.info("[知眸-DB初始化] 新增唯一索引: " + idxName);
        } catch (Exception e) {
            log.debug("[知眸-DB初始化] 唯一索引" + idxName + "可能已存在: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3. zhimou_detection_result 表 (识别结果明细表, 前端展示核心表)
    //    字段顺序严格匹配 entity/ZhimouDetectionResult.java
    // =========================================================================
    private void ensureZhimouDetectionResultTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `zhimou_detection_result` (" +
                "  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
                "  `zhimou_task_id` VARCHAR(128) DEFAULT NULL COMMENT '知眸task_id'," +
                "  `task_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联zhimou_task.id(方便join)'," +
                "  `live_stream_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联直播ID(冗余)'," +
                "  `aircraft_sn` VARCHAR(64) DEFAULT NULL COMMENT '飞机SN'," +
                "  `business_id` VARCHAR(128) DEFAULT NULL," +
                "  `frame_id` BIGINT DEFAULT NULL COMMENT '帧编号/图片编号(image模式下的id)'," +
                "  `image_relative_path` VARCHAR(512) NOT NULL COMMENT '相对save-root-dir的路径,例如20260709/1719990000000_xx.png(统一PNG格式)'," +
                "  `image_format` VARCHAR(16) DEFAULT 'png' COMMENT '统一png(ImageIO强制转码后写入)'," +
                "  `image_width` INT DEFAULT NULL," +
                "  `image_height` INT DEFAULT NULL," +
                "  `image_url_origin` VARCHAR(1024) DEFAULT NULL COMMENT '知眸回调时原始image_url(如果有的话)'," +
                "  `image_has_osd` TINYINT DEFAULT 1 COMMENT '图片是否包含AI标注框:0原图1带OSD标注'," +
                "  `rule_id` VARCHAR(128) DEFAULT NULL," +
                "  `rule_name` VARCHAR(128) DEFAULT NULL," +
                "  `hit_count` INT NOT NULL DEFAULT 0 COMMENT '本次回调总共命中的目标数量'," +
                "  `targets_json` JSON DEFAULT NULL COMMENT '识别目标明细JSON [{class_name,confidence,box,track_id}]'," +
                "  `latitude` DECIMAL(12,9) DEFAULT NULL," +
                "  `longitude` DECIMAL(12,9) DEFAULT NULL," +
                "  `altitude` DECIMAL(10,2) DEFAULT NULL COMMENT '海拔'," +
                "  `height` DECIMAL(10,2) DEFAULT NULL COMMENT '相对起飞点高度'," +
                "  `gimbal_pitch` DECIMAL(8,2) DEFAULT NULL," +
                "  `zoom_factor` DECIMAL(6,2) DEFAULT NULL," +
                "  `event_timestamp_ms` BIGINT NOT NULL COMMENT '事件时间戳(毫秒)'," +
                "  `event_time` DATETIME NOT NULL COMMENT '事件时间(方便SQL时间范围查询)'," +
                "  `raw_callback_json` JSON DEFAULT NULL COMMENT '完整回调JSON存档(排错用)'," +
                "  `work_order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID(后续生成工单时回填)'," +
                "  `deleted` TINYINT NOT NULL DEFAULT 0," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  KEY `idx_zhimou_task_id` (`zhimou_task_id`)," +
                "  KEY `idx_task_id` (`task_id`)," +
                "  KEY `idx_live_stream_id` (`live_stream_id`)," +
                "  KEY `idx_aircraft_sn` (`aircraft_sn`)," +
                "  KEY `idx_rule_id` (`rule_id`)," +
                "  KEY `idx_event_time` (`event_time`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸AI识别结果明细表(前端展示核心表)';";
        jdbcTemplate.execute(sql);
        // 若表已存在, 补齐缺失列 (兼容旧库升级)
        ensureZhimouDetectionColumns();
        log.info("[知眸-DB初始化] zhimou_detection_result 表 OK");
    }

    private void ensureZhimouDetectionColumns() {
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zhimou_detection_result'");
        java.util.Set<String> exists = new java.util.HashSet<>();
        for (Map<String, Object> c : cols) exists.add(String.valueOf(c.get("COLUMN_NAME")).toLowerCase());

        // 按实体类顺序补齐缺失列 (MySQL ADD COLUMN 仅支持 AFTER, 不支持 BEFORE)
        addColumnIfMissing(exists, "zhimou_detection_result", "task_id",
                "BIGINT UNSIGNED DEFAULT NULL COMMENT '关联zhimou_task.id' AFTER `zhimou_task_id`");
        addColumnIfMissing(exists, "zhimou_detection_result", "live_stream_id",
                "BIGINT UNSIGNED DEFAULT NULL COMMENT '关联直播ID(冗余)' AFTER `task_id`");
        addColumnIfMissing(exists, "zhimou_detection_result", "wayline_uuid",
                "VARCHAR(100) DEFAULT NULL COMMENT '航线UUID(飞机执行航线任务时产生)' AFTER `aircraft_sn`");
        addColumnIfMissing(exists, "zhimou_detection_result", "frame_id",
                "BIGINT DEFAULT NULL COMMENT '帧编号/图片编号' AFTER `business_id`");
        addColumnIfMissing(exists, "zhimou_detection_result", "image_width",
                "INT DEFAULT NULL AFTER `image_format`");
        addColumnIfMissing(exists, "zhimou_detection_result", "image_height",
                "INT DEFAULT NULL AFTER `image_width`");
        addColumnIfMissing(exists, "zhimou_detection_result", "image_url_origin",
                "VARCHAR(1024) DEFAULT NULL COMMENT '知眸回调时原始image_url' AFTER `image_height`");
        addColumnIfMissing(exists, "zhimou_detection_result", "image_has_osd",
                "TINYINT DEFAULT 1 COMMENT '图片是否包含AI标注框' AFTER `image_url_origin`");
        addColumnIfMissing(exists, "zhimou_detection_result", "rule_id",
                "VARCHAR(128) DEFAULT NULL AFTER `image_has_osd`");
        addColumnIfMissing(exists, "zhimou_detection_result", "rule_name",
                "VARCHAR(128) DEFAULT NULL AFTER `rule_id`");
        addColumnIfMissing(exists, "zhimou_detection_result", "hit_count",
                "INT NOT NULL DEFAULT 0 COMMENT '本次回调命中目标数' AFTER `rule_name`");
        addColumnIfMissing(exists, "zhimou_detection_result", "gimbal_pitch",
                "DECIMAL(8,2) DEFAULT NULL AFTER `height`");
        addColumnIfMissing(exists, "zhimou_detection_result", "zoom_factor",
                "DECIMAL(6,2) DEFAULT NULL AFTER `gimbal_pitch`");
        addColumnIfMissing(exists, "zhimou_detection_result", "event_timestamp_ms",
                "BIGINT NOT NULL DEFAULT 0 COMMENT '事件时间戳(毫秒)' AFTER `zoom_factor`");
        addColumnIfMissing(exists, "zhimou_detection_result", "raw_callback_json",
                "JSON DEFAULT NULL COMMENT '完整回调JSON存档' AFTER `event_time`");
        addColumnIfMissing(exists, "zhimou_detection_result", "work_order_id",
                "BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID(回填)' AFTER `raw_callback_json`");

        // targets_json / raw_callback_json 升级为 JSON 类型
        upgradeColumnType("zhimou_detection_result", "targets_json", "JSON DEFAULT NULL COMMENT '识别目标明细JSON'");
        upgradeColumnType("zhimou_detection_result", "raw_callback_json", "JSON DEFAULT NULL COMMENT '完整回调JSON存档'");

        // 补齐索引
        addIndexIfMissing("zhimou_detection_result", "idx_task_id", "task_id");
        addIndexIfMissing("zhimou_detection_result", "idx_live_stream_id", "live_stream_id");
        addIndexIfMissing("zhimou_detection_result", "idx_aircraft_sn", "aircraft_sn");
    }
}
