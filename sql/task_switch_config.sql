-- ============================================================
-- task_switch_config 数据源开关配置表
-- ============================================================
-- 创建日期: 2026-08-25
-- 用途: 集中管理视频任务/图片任务/警情数据源等开关
--       admin-v3 "换数据源" 页面操作
-- ============================================================

DROP TABLE IF EXISTS `task_switch_config`;
CREATE TABLE `task_switch_config` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `switch_key`  VARCHAR(32)  NOT NULL COMMENT '开关键名: video_task / image_task / alert_data_source',
  `value`       VARCHAR(16)  NOT NULL COMMENT '开关值: video_task/image_task 为 0/1; alert_data_source 为 video/image',
  `update_time` DATETIME     DEFAULT NULL COMMENT '最后修改时间',
  `updater`     VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_switch_key` (`switch_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源开关配置表';

-- 初始化数据
INSERT INTO `task_switch_config` (`switch_key`, `value`, `update_time`, `updater`) VALUES
('video_task',         '1',     NOW(), 'system'),
('image_task',         '0',     NOW(), 'system'),
('alert_data_source',  'video', NOW(), 'system');

-- 验证
-- SELECT * FROM task_switch_config;
