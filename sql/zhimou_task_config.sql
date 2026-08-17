CREATE TABLE IF NOT EXISTS `zhimou_task_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `zapk_id` VARCHAR(50) DEFAULT NULL COMMENT '应用包ID',
  `app_ids` TEXT COMMENT '应用ID列表(JSON数组格式)',
  `report_url` VARCHAR(255) DEFAULT NULL COMMENT '回调地址',
  `output_url` VARCHAR(255) DEFAULT NULL COMMENT '输出地址',
  `osd_display_name` TINYINT(1) DEFAULT 1 COMMENT '是否显示名称',
  `osd_display_box` TINYINT(1) DEFAULT 1 COMMENT '是否显示框',
  `osd_display_track` TINYINT(1) DEFAULT 1 COMMENT '是否显示轨迹',
  `osd_font_size` INT DEFAULT 0 COMMENT '字体大小',
  `osd_border_size` INT DEFAULT 0 COMMENT '边框大小',
  `deleted` TINYINT(1) DEFAULT 0 COMMENT '删除标记',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸任务配置表';