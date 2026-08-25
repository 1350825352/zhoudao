-- ============================================================
-- 升级SQL: 给 zhimou_image_detection 和 self_model_image_detection 表
--          新增 latitude / longitude / altitude 字段及索引
--          (图片自带 EXIF GPS 信息, 需保存到识别结果表)
-- 执行环境: MySQL 5.7+ / 8.0+
-- 执行数据库: 192.168.2.200:3306/sikong_cloud_api
-- 生成日期: 2026-08-24
-- 说明: 两张表已存在, 此脚本为增量升级, 不重建表, 不影响现有数据
-- ============================================================

-- ----------------------------------------------------------
-- 表1: zhimou_image_detection 新增 GPS 字段
-- ----------------------------------------------------------
ALTER TABLE `zhimou_image_detection`
  ADD COLUMN `latitude`  DECIMAL(10,7) DEFAULT NULL COMMENT '纬度(EXIF GPS, 正数北纬 负数南纬)' AFTER `frame_enhance`,
  ADD COLUMN `longitude` DECIMAL(11,7) DEFAULT NULL COMMENT '经度(EXIF GPS, 正数东经 负数西经)' AFTER `latitude`,
  ADD COLUMN `altitude`  DECIMAL(8,3)  DEFAULT NULL COMMENT '海拔高度(米, EXIF GPSAltitude)'    AFTER `longitude`;

ALTER TABLE `zhimou_image_detection`
  ADD INDEX `idx_latitude`  (`latitude`),
  ADD INDEX `idx_longitude` (`longitude`);


-- ----------------------------------------------------------
-- 表2: self_model_image_detection 新增 GPS 字段
-- ----------------------------------------------------------
ALTER TABLE `self_model_image_detection`
  ADD COLUMN `latitude`  DECIMAL(10,7) DEFAULT NULL COMMENT '纬度(EXIF GPS, 正数北纬 负数南纬)' AFTER `image_format`,
  ADD COLUMN `longitude` DECIMAL(11,7) DEFAULT NULL COMMENT '经度(EXIF GPS, 正数东经 负数西经)' AFTER `latitude`,
  ADD COLUMN `altitude`  DECIMAL(8,3)  DEFAULT NULL COMMENT '海拔高度(米, EXIF GPSAltitude)'    AFTER `longitude`;

ALTER TABLE `self_model_image_detection`
  ADD INDEX `idx_latitude`  (`latitude`),
  ADD INDEX `idx_longitude` (`longitude`);


-- ============================================================
-- 验证SQL (执行完上面后跑一下, 确认字段已添加)
-- ============================================================
-- SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = 'sikong_cloud_api'
--   AND TABLE_NAME IN ('zhimou_image_detection', 'self_model_image_detection')
--   AND COLUMN_NAME IN ('latitude', 'longitude', 'altitude')
-- ORDER BY TABLE_NAME, ORDINAL_POSITION;
