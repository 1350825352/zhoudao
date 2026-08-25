-- ============================================================
-- 图片识别结果表 (知眸图片识别 + 自有模型图片识别)
-- 两张表完全独立,无外键关联,各自保存回调/响应的完整信息
-- 图片名称字段(local_image_name)为后续数据清洗预留
-- 生成日期: 2026-08-18
-- 更新日期: 2026-08-24 新增 latitude/longitude/altitude 字段及索引(图片自带 EXIF GPS)
-- ============================================================

-- ----------------------------------------------------------
-- 表1: 知眸图片识别结果表
--   数据来源: 知眸图片任务异步回调 (POST /api/zhimou/callback)
--   回调结构: {event,task_id,zapk_id,device_id,business_id,timestamp,data:{frame,apps[]}}
-- ----------------------------------------------------------
DROP TABLE IF EXISTS `zhimou_image_detection`;
CREATE TABLE `zhimou_image_detection` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

  -- ====== 知眸任务关联 ======
  `zhimou_task_id`      VARCHAR(128) DEFAULT NULL COMMENT '知眸task_id(创建任务时返回)',
  `business_id`         VARCHAR(128) DEFAULT NULL COMMENT '业务ID(创建任务时我方传入)',
  `zapk_id`             VARCHAR(128) DEFAULT NULL COMMENT '知眸应用包ID',
  `app_id`              VARCHAR(128) DEFAULT NULL COMMENT '命中的应用ID(单个图片回调通常1个app)',
  `app_name`            VARCHAR(128) DEFAULT NULL COMMENT '应用名称(如:小客车识别/人员识别)',
  `device_id`           VARCHAR(64)  DEFAULT NULL COMMENT '设备ID(飞机SN)',

  -- ====== 图片信息(核心:图片名称为数据清洗依据) ======
  `local_image_name`    VARCHAR(255) DEFAULT NULL COMMENT '本地保存的图片文件名(如DJI_20260806165701_0001_V),数据清洗时关联同一张照片的关键字段',
  `zhimou_image_path`   VARCHAR(1024) DEFAULT NULL COMMENT '知眸回调里frame.image原始路径(如/openapi/{task_id}/images/{ts}.jpg)',
  `zhimou_app_image`    VARCHAR(1024) DEFAULT NULL COMMENT '知眸app叠加标注后图片路径(/openapi/{task_id}/messages/{app_id}/{ts}.jpg)',
  `local_image_path`    VARCHAR(512) DEFAULT NULL COMMENT '本地落盘的相对路径(年月日/文件名.png)',
  `image_width`         INT          DEFAULT NULL COMMENT '图片宽度(像素)',
  `image_height`        INT          DEFAULT NULL COMMENT '图片高度(像素)',
  `frame_id`            BIGINT       DEFAULT NULL COMMENT '帧ID(图片模式下一般为0)',
  `frame_enhance`       INT          DEFAULT NULL COMMENT 'frame.enhance增强参数',

  -- ====== 图片EXIF GPS信息 (无人机航拍照片自带) ======
  `latitude`            DECIMAL(10,7) DEFAULT NULL COMMENT '纬度(EXIF GPS, 正数北纬 负数南纬)',
  `longitude`           DECIMAL(11,7) DEFAULT NULL COMMENT '经度(EXIF GPS, 正数东经 负数西经)',
  `altitude`            DECIMAL(8,3)  DEFAULT NULL COMMENT '海拔高度(米, EXIF GPSAltitude)',

  -- ====== 识别结果(保留apps[0].objects的完整信息) ======
  `hit_count`           INT NOT NULL DEFAULT 0 COMMENT '本次回调命中目标总数',
  `targets_json`        JSON DEFAULT NULL COMMENT 'objects数组原文JSON [{class_id,name,color,x,y,track_id,confidence,description}]',

  -- ====== 时间 ======
  `event_timestamp_ms`  BIGINT NOT NULL COMMENT '知眸回调timestamp(毫秒)',
  `event_time`          DATETIME NOT NULL COMMENT '事件时间(由timestamp转)',
  `received_time`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收到回调的时间',

  -- ====== 原始数据 ======
  `raw_callback_json`   JSON DEFAULT NULL COMMENT '完整回调JSON存档(排错/审计用,包含event/task_id/data全部字段)',
  `callback_event`      VARCHAR(32) DEFAULT NULL COMMENT '回调事件类型: start/message/stop',

  -- ====== 数据清洗标记(为后续清洗流程预留) ======
  `clean_status`        TINYINT NOT NULL DEFAULT 0 COMMENT '清洗状态:0未清洗1已清洗2判定为误识别',
  `clean_time`          DATETIME DEFAULT NULL COMMENT '清洗时间',
  `clean_remark`        VARCHAR(512) DEFAULT NULL COMMENT '清洗备注',

  -- ====== 工单关联 ======
  `work_order_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID',

  -- ====== 标准字段 ======
  `deleted`             TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_zhimou_task_id`  (`zhimou_task_id`),
  KEY `idx_business_id`    (`business_id`),
  KEY `idx_local_image`    (`local_image_name`),
  KEY `idx_device_id`      (`device_id`),
  KEY `idx_event_time`     (`event_time`),
  KEY `idx_clean_status`   (`clean_status`),
  KEY `idx_latitude`       (`latitude`),
  KEY `idx_longitude`      (`longitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸图片识别结果表(独立于视频识别,配合自有模型做数据清洗)';


-- ----------------------------------------------------------
-- 表2: 自有模型图片识别结果表
--   数据来源: 自有识别模型同步API (POST http://192.168.2.200:5173/api/v1/detect)
--   响应结构: {code,message,data:{request_id,model_version,image_path,image_info,detections[],detection_count,...}}
--   注: 与知眸完全独立,不做关联
-- ----------------------------------------------------------
DROP TABLE IF EXISTS `self_model_image_detection`;
CREATE TABLE `self_model_image_detection` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

  -- ====== 自有模型标识 ======
  `request_id`          VARCHAR(128) DEFAULT NULL COMMENT '自有模型返回的request_id(req_时间戳_hex,由服务器生成)',
  `model_version`       VARCHAR(64)  DEFAULT NULL COMMENT '模型版本(如 mingp)',
  `model_device`        VARCHAR(32)  DEFAULT NULL COMMENT '推理设备(cpu/cuda:0)',
  `server_url`          VARCHAR(255) DEFAULT NULL COMMENT '服务器地址(http://192.168.2.200:5173)',

  -- ====== 图片信息(核心:图片名称为数据清洗依据) ======
  `local_image_name`    VARCHAR(255) DEFAULT NULL COMMENT '本地图片文件名(上传时的原始文件名,数据清洗时关联同一张照片的关键字段)',
  `local_image_path`    VARCHAR(512) DEFAULT NULL COMMENT '本地落盘的相对路径(年月日/文件名.png)',
  `server_image_path`   VARCHAR(512) DEFAULT NULL COMMENT '自有模型返回的image_path(data/images/req_xxx.png)',
  `image_width`         INT          DEFAULT NULL COMMENT '图片宽度(像素)',
  `image_height`        INT          DEFAULT NULL COMMENT '图片高度(像素)',
  `image_format`        VARCHAR(16)  DEFAULT NULL COMMENT '图片格式(png/jpg)',

  -- ====== 图片EXIF GPS信息 (无人机航拍照片自带) ======
  `latitude`            DECIMAL(10,7) DEFAULT NULL COMMENT '纬度(EXIF GPS, 正数北纬 负数南纬)',
  `longitude`           DECIMAL(11,7) DEFAULT NULL COMMENT '经度(EXIF GPS, 正数东经 负数西经)',
  `altitude`            DECIMAL(8,3)  DEFAULT NULL COMMENT '海拔高度(米, EXIF GPSAltitude)',

  -- ====== 识别结果(保留detections数组完整信息) ======
  `hit_count`           INT NOT NULL DEFAULT 0 COMMENT '识别到的目标总数(detection_count)',
  `targets_json`        JSON DEFAULT NULL COMMENT 'detections数组原文JSON [{id,class_id,class_name,category,confidence,bbox{x1,y1,x2,y2}}]',

  -- ====== 调用参数 ======
  `threshold`           DECIMAL(4,3) DEFAULT NULL COMMENT '调用时传入的置信度阈值',
  `processing_time_ms`  INT          DEFAULT NULL COMMENT '服务器处理耗时(毫秒)',
  `processed_at`        VARCHAR(32)  DEFAULT NULL COMMENT '服务器处理时间(ISO格式字符串)',
  `call_time`           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '我方调用接口的时间',

  -- ====== 调用结果状态 ======
  `call_status`         TINYINT NOT NULL DEFAULT 0 COMMENT '调用状态:0未知1成功2失败(HTTP非200或code!=0)',
  `error_message`       VARCHAR(512) DEFAULT NULL COMMENT '调用失败时的错误信息',

  -- ====== 原始数据 ======
  `raw_response_json`   JSON DEFAULT NULL COMMENT '完整响应JSON存档(排错/审计用)',

  -- ====== 数据清洗标记(为后续清洗流程预留) ======
  `clean_status`        TINYINT NOT NULL DEFAULT 0 COMMENT '清洗状态:0未清洗1已清洗2判定为误识别',
  `clean_time`          DATETIME DEFAULT NULL COMMENT '清洗时间',
  `clean_remark`        VARCHAR(512) DEFAULT NULL COMMENT '清洗备注',

  -- ====== 工单关联 ======
  `work_order_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID',

  -- ====== 标准字段 ======
  `deleted`             TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  KEY `idx_request_id`      (`request_id`),
  KEY `idx_local_image`     (`local_image_name`),
  KEY `idx_model_version`   (`model_version`),
  KEY `idx_call_time`       (`call_time`),
  KEY `idx_clean_status`    (`clean_status`),
  KEY `idx_latitude`        (`latitude`),
  KEY `idx_longitude`       (`longitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自有模型图片识别结果表(独立于知眸,同步调用落库)';
