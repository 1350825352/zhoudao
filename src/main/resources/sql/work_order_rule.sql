CREATE TABLE IF NOT EXISTS `work_order_rule` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `alarm_type` VARCHAR(100) COMMENT '告警类型（为空则匹配所有）',
    `start_time` TIME DEFAULT '00:00:00' COMMENT '开始时间',
    `end_time` TIME DEFAULT '23:59:59' COMMENT '结束时间',
    `aircraft_sn` VARCHAR(100) COMMENT '飞机SN（为空则匹配所有）',
    `handler_user_id` BIGINT NOT NULL COMMENT '处理人用户ID',
    `handler_name` VARCHAR(100) COMMENT '处理人名称',
    `handler_email` VARCHAR(100) COMMENT '处理人邮箱',
    `sort_order` INT DEFAULT 0 COMMENT '排序优先级（数字越小优先级越高）',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0禁用，1启用',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除：0否，1是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_alarm_type` (`alarm_type`),
    INDEX `idx_aircraft_sn` (`aircraft_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单发送规则表';