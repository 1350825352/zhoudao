-- ============================================================
-- 大疆司空2 + 上云API 整合后台 数据库初始化脚本
-- 使用说明：在Navicat中先选中目标数据库（如dockzhimou）双击使其成为当前数据库，再执行本脚本
-- ============================================================
-- 注意：本脚本不再自动创建和切换数据库，请确保当前默认数据库就是你想使用的库
-- （在Navicat左侧dockzhimou上双击，它会变绿/加粗表示已选中）

-- -----------------------------------------------------------
-- 1. 设备基础信息表（网关：机场/遥控器 + 子设备：飞机/负载）
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `device`;
CREATE TABLE `device` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sn`              VARCHAR(64)  NOT NULL COMMENT '设备SN(序列号)',
    `device_name`     VARCHAR(128) DEFAULT NULL COMMENT '设备名称/别名',
    `device_type`     TINYINT      NOT NULL COMMENT '设备类型: 1-机场(网关) 2-遥控器(网关) 3-飞行器 4-负载(相机) 5-喊话器 6-探照灯',
    `device_model`    VARCHAR(64)  DEFAULT NULL COMMENT '设备型号: 如大疆机场3、Matrice 4TD、H20T等',
    `model_key`       VARCHAR(32)  DEFAULT NULL COMMENT '物模型标识: 如 0-67-0',
    `gateway_sn`      VARCHAR(64)  DEFAULT NULL COMMENT '所属网关SN(飞机/负载填机场或遥控器SN)',
    `org_uuid`        VARCHAR(64)  DEFAULT NULL COMMENT '所属组织UUID',
    `project_uuid`    VARCHAR(64)  DEFAULT NULL COMMENT '所属项目UUID',
    `online_status`   TINYINT      NOT NULL DEFAULT 0 COMMENT '在线状态: 0-离线 1-在线',
    `paired_status`   TINYINT      DEFAULT 0 COMMENT '配对状态: 0-未配对 1-已配对',
    `firmware_version` VARCHAR(64) DEFAULT NULL COMMENT '固件版本',
    `last_online_time` DATETIME    DEFAULT NULL COMMENT '最后上线时间',
    `last_offline_time` DATETIME   DEFAULT NULL COMMENT '最后离线时间',
    `bind_code`       VARCHAR(64)  DEFAULT NULL COMMENT '设备绑定码',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0-未删 1-已删',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sn` (`sn`),
    KEY `idx_gateway_sn` (`gateway_sn`),
    KEY `idx_device_type` (`device_type`),
    KEY `idx_online_status` (`online_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备基础信息表';

-- -----------------------------------------------------------
-- 2. 飞机镜头(负载)明细表 - 针对飞行器的各个相机镜头
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `aircraft_camera`;
CREATE TABLE `aircraft_camera` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `aircraft_sn`     VARCHAR(64)  NOT NULL COMMENT '飞行器SN',
    `camera_index`    VARCHAR(32)  NOT NULL COMMENT '相机索引: 如 165-0-7',
    `camera_model`    VARCHAR(64)  DEFAULT NULL COMMENT '镜头型号: 广角/变焦/红外',
    `camera_type`     TINYINT      DEFAULT NULL COMMENT '镜头类型: 1-广角 2-变焦 3-红外测温 4-可见光',
    `lens_type`       VARCHAR(32)  DEFAULT NULL COMMENT '物模型lens_type标识',
    `pixel`           VARCHAR(32)  DEFAULT NULL COMMENT '像素',
    `focal_length`    VARCHAR(32)  DEFAULT NULL COMMENT '焦距',
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_aircraft_camera` (`aircraft_sn`, `camera_index`),
    KEY `idx_aircraft_sn` (`aircraft_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞行器镜头明细表';

-- -----------------------------------------------------------
-- 3. 设备实时状态表 - 来自MQTT的osd/state，最新一条
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `device_realtime_status`;
CREATE TABLE `device_realtime_status` (
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_sn`           VARCHAR(64)  NOT NULL COMMENT '设备SN',
    `gateway_sn`          VARCHAR(64)  DEFAULT NULL COMMENT '网关SN',

    -- ====== 位置相关 ======
    `latitude`            DECIMAL(12, 9) DEFAULT NULL COMMENT '纬度',
    `longitude`           DECIMAL(12, 9) DEFAULT NULL COMMENT '经度',
    `height`              DECIMAL(10, 3) DEFAULT NULL COMMENT '相对高度(米)',
    `vertical_speed`      DECIMAL(8, 3)  DEFAULT NULL COMMENT '垂直速度(m/s)',
    `horizontal_speed`    DECIMAL(8, 3)  DEFAULT NULL COMMENT '水平速度(m/s)',
    `home_latitude`       DECIMAL(12, 9) DEFAULT NULL COMMENT '返航点(Home)纬度',
    `home_longitude`      DECIMAL(12, 9) DEFAULT NULL COMMENT '返航点(Home)经度',
    `distance_to_home`    DECIMAL(10, 2) DEFAULT NULL COMMENT '距离Home点的距离(米)',

    -- ====== 飞行/设备状态 ======
    `device_mode`         INT         DEFAULT NULL COMMENT '设备/飞行模式码',
    `flight_status`       VARCHAR(32) DEFAULT NULL COMMENT '飞行状态: 空闲/飞行中/起飞/降落/返航/充电等',
    `drone_in_dock`       TINYINT     DEFAULT NULL COMMENT '飞机是否在机场内: 0-不在 1-在',
    `cover_state`         TINYINT     DEFAULT NULL COMMENT '机场仓盖状态: 0-关闭 1-打开',

    -- ====== 电池相关 ======
    `battery_percent`     INT         DEFAULT NULL COMMENT '剩余电量百分比(0-100)',
    `battery_temperature` DECIMAL(5, 2) DEFAULT NULL COMMENT '电池温度(摄氏度)',
    `battery_voltage`     INT         DEFAULT NULL COMMENT '电池电压(mV)',
    `battery_current`     INT         DEFAULT NULL COMMENT '电池电流(mA)',
    `charge_state`        TINYINT     DEFAULT NULL COMMENT '充电状态: 0-未充 1-充电中 2-充满',

    -- ====== GPS/RTK ======
    `gps_satellite_count` INT         DEFAULT NULL COMMENT 'GPS搜星数量',
    `rtk_satellite_count` INT         DEFAULT NULL COMMENT 'RTK搜星数量',
    `position_quality`    INT         DEFAULT NULL COMMENT '定位质量',
    `rtk_fixed`           TINYINT     DEFAULT NULL COMMENT 'RTK是否固定解: 0-否 1-是',
    `calibration_state`   TINYINT     DEFAULT NULL COMMENT '标定状态',

    -- ====== 环境 ======
    `wind_speed`          DECIMAL(6, 2) DEFAULT NULL COMMENT '风速(m/s)',
    `environment_temperature` DECIMAL(5,2) DEFAULT NULL COMMENT '环境温度(摄氏度)',
    `humidity`            INT         DEFAULT NULL COMMENT '湿度(%)',
    `rainfall`            INT         DEFAULT NULL COMMENT '降雨状态: 0-无雨 非0-有雨',

    -- ====== 网络/链路 ======
    `network_type`        INT         DEFAULT NULL COMMENT '网络类型',
    `network_quality`     INT         DEFAULT NULL COMMENT '网络质量',
    `sdr_link_state`      INT         DEFAULT NULL COMMENT '无线图传链路状态',
    `sdr_quality`         INT         DEFAULT NULL COMMENT '无线图传质量',
    `4g_link_state`       INT         DEFAULT NULL COMMENT '4G链路状态',
    `4g_quality`          INT         DEFAULT NULL COMMENT '4G链路质量',

    -- ====== 任务 ======
    `current_task_id`     VARCHAR(64) DEFAULT NULL COMMENT '当前飞行任务ID',
    `current_task_name`   VARCHAR(128) DEFAULT NULL COMMENT '当前飞行任务名称',
    `flight_step_code`    INT         DEFAULT NULL COMMENT '飞行任务步骤码',

    -- ====== 存储 ======
    `storage_total`       BIGINT      DEFAULT NULL COMMENT '总存储(KB)',
    `storage_used`        BIGINT      DEFAULT NULL COMMENT '已用存储(KB)',

    `osd_raw_json`        JSON        DEFAULT NULL COMMENT '原始osd JSON(完整快照)',
    `update_time`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_sn` (`device_sn`),
    KEY `idx_gateway_sn` (`gateway_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备实时状态表(最新一条)';

-- -----------------------------------------------------------
-- 4. 航线信息表 - 来自司空2 OpenAPI同步的数据
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `wayline`;
CREATE TABLE `wayline` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `wayline_uuid`      VARCHAR(64)  NOT NULL COMMENT '司空2航线UUID',
    `wayline_name`      VARCHAR(128) NOT NULL COMMENT '航线名称',
    `project_uuid`      VARCHAR(64)  DEFAULT NULL COMMENT '所属项目UUID',
    `project_name`      VARCHAR(128) DEFAULT NULL COMMENT '项目名称',
    `wayline_type`      TINYINT      DEFAULT NULL COMMENT '航线类型',
    `template_type`     TINYINT      DEFAULT NULL COMMENT '模板类型: 航点/建图/正射/倾斜等',
    `wpml_object_key`   VARCHAR(512) DEFAULT NULL COMMENT '航线文件在对象存储的key',
    `wpml_download_url` VARCHAR(1024) DEFAULT NULL COMMENT '航线文件下载地址',
    `total_distance`    DECIMAL(12, 2) DEFAULT NULL COMMENT '航线总长度(米)',
    `estimated_time`    INT          DEFAULT NULL COMMENT '预计飞行时间(秒)',
    `waypoint_count`    INT          DEFAULT NULL COMMENT '航点数',
    `height`            DECIMAL(8, 2) DEFAULT NULL COMMENT '飞行高度',
    `speed`             DECIMAL(6, 2) DEFAULT NULL COMMENT '飞行速度',
    `action_count`      INT          DEFAULT NULL COMMENT '动作数',
    `sync_source`       TINYINT      NOT NULL DEFAULT 1 COMMENT '来源: 1-手动同步自司空2 2-手动创建',
    `last_sync_time`    DATETIME     DEFAULT NULL COMMENT '最后同步时间',
    `raw_json`          JSON         DEFAULT NULL COMMENT '司空2原始数据',
    `remark`            VARCHAR(255) DEFAULT NULL,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wayline_uuid` (`wayline_uuid`),
    KEY `idx_project_uuid` (`project_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='航线信息表';

-- -----------------------------------------------------------
-- 5. 飞行任务表 - 执行航线生成的任务
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `flight_task`;
CREATE TABLE `flight_task` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_uuid`        VARCHAR(64)  DEFAULT NULL COMMENT '司空2任务UUID(返回的task_uuid)',
    `task_name`        VARCHAR(128) NOT NULL COMMENT '任务名称',
    `project_uuid`     VARCHAR(64)  DEFAULT NULL COMMENT '项目UUID',
    `wayline_uuid`     VARCHAR(64)  DEFAULT NULL COMMENT '航线UUID',
    `wayline_name`     VARCHAR(128) DEFAULT NULL COMMENT '航线名称',

    `aircraft_sn`      VARCHAR(64)  DEFAULT NULL COMMENT '执行飞机SN',
    `aircraft_name`    VARCHAR(128) DEFAULT NULL COMMENT '执行飞机名称/别名',
    `gateway_sn`       VARCHAR(64)  DEFAULT NULL COMMENT '机场SN(网关)',

    `task_status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '任务状态: 0-待执行 1-已下发 2-执行中 3-已完成 4-已暂停 5-已取消 6-失败 7-超时',
    `status_message`   VARCHAR(255) DEFAULT NULL COMMENT '状态说明',
    `step_code`        INT          DEFAULT NULL COMMENT '步骤码',
    `progress`         INT          DEFAULT 0 COMMENT '进度(0-100)',

    `plan_start_time`  DATETIME     DEFAULT NULL COMMENT '计划开始时间',
    `actual_start_time` DATETIME    DEFAULT NULL COMMENT '实际开始时间',
    `actual_end_time`  DATETIME     DEFAULT NULL COMMENT '实际结束时间',

    `operator`         VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    `execute_type`     TINYINT      DEFAULT 1 COMMENT '执行方式: 1-司空2任务下发 2-上云API机场航线(mqtt services)',
    `result_code`      INT          DEFAULT NULL COMMENT '结果码',
    `bid`              VARCHAR(64)  DEFAULT NULL COMMENT 'MQTT业务ID',
    `request_json`     JSON         DEFAULT NULL COMMENT '下发请求JSON',
    `response_json`    JSON         DEFAULT NULL COMMENT '返回JSON',
    `deleted`          TINYINT      NOT NULL DEFAULT 0,
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_uuid` (`task_uuid`),
    KEY `idx_aircraft_sn` (`aircraft_sn`),
    KEY `idx_task_status` (`task_status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞行任务表';

-- -----------------------------------------------------------
-- 6. 直播推流任务表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `live_stream`;
CREATE TABLE `live_stream` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `stream_id`         VARCHAR(128) DEFAULT NULL COMMENT '司空2直播ID/码流转发器ID',
    `stream_name`       VARCHAR(128) DEFAULT NULL COMMENT '直播名称',
    `gateway_sn`        VARCHAR(64)  DEFAULT NULL COMMENT '机场SN(网关)',
    `aircraft_sn`       VARCHAR(64)  DEFAULT NULL COMMENT '飞机SN',
    `camera_index`      VARCHAR(32)  DEFAULT NULL COMMENT '相机索引: 如165-0-7',

    `push_type`         TINYINT      NOT NULL DEFAULT 1 COMMENT '推流方式: 1-司空2旁路推流 2-上云API services推流',
    `stream_protocol`   VARCHAR(16)  DEFAULT 'rtmp' COMMENT '协议: rtmp/rtsp/webrtc/gb28181',
    `quality_type`      VARCHAR(16)  DEFAULT 'adaptive' COMMENT '画质: adaptive/fluent/ultraHD',
    `video_expire`      INT          DEFAULT 7200 COMMENT '鉴权有效期(秒)',

    `push_url`          VARCHAR(1024) DEFAULT NULL COMMENT '推流地址(旁路推流目标)',
    `pull_url`          VARCHAR(1024) DEFAULT NULL COMMENT '拉流地址',
    `url_type`          VARCHAR(32)  DEFAULT NULL COMMENT '供应商类型: srs/volc/agora等',

    `status`            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0-未开始 1-推流中 2-已停止 3-失败',
    `status_message`    VARCHAR(255) DEFAULT NULL COMMENT '状态说明/错误原因',
    `start_time`        DATETIME     DEFAULT NULL COMMENT '开始时间',
    `stop_time`         DATETIME     DEFAULT NULL COMMENT '停止时间',
    `expire_time`       DATETIME     DEFAULT NULL COMMENT '过期时间',

    `request_json`      JSON         DEFAULT NULL,
    `response_json`     JSON         DEFAULT NULL,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_gateway_sn` (`gateway_sn`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播推流任务表';

-- -----------------------------------------------------------
-- 7. MQTT消息日志表（调试用，可选）
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `mqtt_message_log`;
CREATE TABLE `mqtt_message_log` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `topic`       VARCHAR(255) NOT NULL COMMENT 'Topic',
    `direction`   TINYINT      NOT NULL COMMENT '方向: 1-订阅收到(上行) 2-发布发出(下行)',
    `gateway_sn`  VARCHAR(64)  DEFAULT NULL,
    `device_sn`   VARCHAR(64)  DEFAULT NULL,
    `method`      VARCHAR(64)  DEFAULT NULL,
    `tid`         VARCHAR(64)  DEFAULT NULL,
    `bid`         VARCHAR(64)  DEFAULT NULL,
    `payload`     JSON         DEFAULT NULL COMMENT '消息体',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_topic` (`topic`),
    KEY `idx_gateway_sn` (`gateway_sn`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQTT消息日志表(可选开启)';

-- -----------------------------------------------------------
-- 8. 项目/组织信息缓存表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `organization_project`;
CREATE TABLE `organization_project` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `org_uuid`     VARCHAR(64)  NOT NULL COMMENT '组织UUID',
    `org_name`     VARCHAR(128) NOT NULL COMMENT '组织名称',
    `project_uuid` VARCHAR(64)  NOT NULL COMMENT '项目UUID',
    `project_name` VARCHAR(128) NOT NULL COMMENT '项目名称',
    `introduction` VARCHAR(512) DEFAULT NULL COMMENT '项目简介',
    `center_latitude`  DECIMAL(12, 9) DEFAULT NULL COMMENT '工作中心点纬度',
    `center_longitude` DECIMAL(12, 9) DEFAULT NULL COMMENT '工作中心点经度',
    `last_sync_time` DATETIME DEFAULT NULL,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_uuid` (`project_uuid`),
    KEY `idx_org_uuid` (`org_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织项目缓存表';

-- ============================================================
-- 以下为: 知眸AI视频分析平台集成表结构 (2026-07新增)
-- ============================================================

-- -----------------------------------------------------------
-- 9. 直播表扩展: 增加知眸AI任务关联字段
-- (老库升级时单独执行这一段ALTER, 新库直接建表时就包含, 所以这里单独保留供手工执行)
-- -----------------------------------------------------------
-- ALTER TABLE `live_stream`
--     ADD COLUMN `zhimou_task_id`          VARCHAR(128) DEFAULT NULL COMMENT '知眸任务ID(对应 POST /api/task 返回的data.task_id)' AFTER `response_json`,
--     ADD COLUMN `zhimou_app_id`           VARCHAR(128) DEFAULT NULL COMMENT '知眸前端选的应用ID(创建任务必填)' AFTER `zhimou_task_id`,
--     ADD COLUMN `zhimou_pkg_id`           VARCHAR(128) DEFAULT NULL COMMENT '知眸应用包ID(前端先选应用包)' AFTER `zhimou_app_id`,
--     ADD COLUMN `zhimou_status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '知眸任务状态: 0-未创建 1-运行中 2-已成功关闭 3-创建失败 4-调用关闭失败' AFTER `zhimou_pkg_id`,
--     ADD COLUMN `zhimou_start_time`       DATETIME     DEFAULT NULL COMMENT '知眸任务开始时间' AFTER `zhimou_status`,
--     ADD COLUMN `zhimou_stop_time`        DATETIME     DEFAULT NULL COMMENT '知眸任务停止时间' AFTER `zhimou_start_time`,
--     ADD COLUMN `zhimou_last_error`       VARCHAR(512) DEFAULT NULL COMMENT '知眸最后一次错误信息' AFTER `zhimou_stop_time`,
--     ADD KEY `idx_zhimou_task_id` (`zhimou_task_id`),
--     ADD KEY `idx_zhimou_status` (`zhimou_status`);

-- -----------------------------------------------------------
-- 10. 知眸AI识别任务表 (与live_stream一对一, 也可扩展为一对多: 一个直播对应多个应用包识别)
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `zhimou_task`;
CREATE TABLE `zhimou_task` (
    `id`                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `live_stream_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联live_stream.id',
    `zhimou_task_id`          VARCHAR(128)  NOT NULL COMMENT '知眸平台返回的任务ID(唯一)',

    `gateway_sn`              VARCHAR(64)   DEFAULT NULL COMMENT '机场SN(冗余方便查)',
    `aircraft_sn`             VARCHAR(64)   DEFAULT NULL COMMENT '飞机SN(冗余方便查)',
    `camera_index`            VARCHAR(32)   DEFAULT NULL COMMENT '相机索引(冗余)',

    `app_id`                  VARCHAR(128)  NOT NULL COMMENT '前端选的应用ID',
    `application_package_id`  VARCHAR(128)  DEFAULT NULL COMMENT '前端选的应用包ID',
    `business_id`             VARCHAR(128)  DEFAULT NULL COMMENT '我方业务ID(传=live_stream.id)',

    `mode`                    VARCHAR(16)   NOT NULL DEFAULT 'video' COMMENT '任务模式: video / image',
    `input_url`               VARCHAR(1024) DEFAULT NULL COMMENT '传给知眸的输入视频流地址(SRS RTMP)',
    `output_url`              VARCHAR(1024) DEFAULT NULL COMMENT '知眸AI叠加标注后的输出流地址(可空)',
    `report_url`              VARCHAR(1024) DEFAULT NULL COMMENT '回调地址(等于application.yml里的callback-url)',

    `status`                  TINYINT       NOT NULL DEFAULT 0 COMMENT '状态: 0-待创建 1-运行中 2-已关闭 3-创建失败 4-关闭失败 5-知眸自动关闭(超时)',
    `status_message`          VARCHAR(512)  DEFAULT NULL COMMENT '状态说明/错误详情',

    `start_time`              DATETIME      DEFAULT NULL COMMENT '任务开始时间',
    `stop_time`               DATETIME      DEFAULT NULL COMMENT '任务停止时间',

    `request_json`            JSON          DEFAULT NULL COMMENT '创建任务时发送给知眸的完整JSON',
    `response_json`           JSON          DEFAULT NULL COMMENT '知眸返回的创建任务响应JSON',
    `last_error`              VARCHAR(1024) DEFAULT NULL COMMENT '最后一次错误',

    `deleted`                 TINYINT       NOT NULL DEFAULT 0,
    `create_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_zhimou_task_id` (`zhimou_task_id`),
    KEY `idx_live_stream_id` (`live_stream_id`),
    KEY `idx_aircraft_sn` (`aircraft_sn`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸AI识别任务表';

-- -----------------------------------------------------------
-- 11. 知眸AI识别结果明细表 (核心! 前端显示的图片/经纬度/报警时间都来自这张表)
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `zhimou_detection_result`;
CREATE TABLE `zhimou_detection_result` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `zhimou_task_id`       VARCHAR(128)  NOT NULL COMMENT '知眸任务ID(对应zhimou_task.zhimou_task_id)',
    `task_id`              BIGINT UNSIGNED DEFAULT NULL COMMENT '关联zhimou_task.id(方便join)',
    `live_stream_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联直播ID(冗余)',
    `aircraft_sn`          VARCHAR(64)   DEFAULT NULL COMMENT '飞机SN(冗余方便查)',

    `business_id`          VARCHAR(128)  DEFAULT NULL COMMENT '我方业务ID(回调里原样回传)',
    `frame_id`             BIGINT        DEFAULT NULL COMMENT '帧编号/图片编号(image模式下的id)',

    -- ------------------ 图片信息: 前端要展示的图片 ------------------
    `image_relative_path`  VARCHAR(512)  NOT NULL COMMENT '图片相对路径(按年月日分目录): 20260709/1719990000000_abcd.png.  前端访问时拼成: /api/zhimou-img/{image_relative_path}',
    `image_format`         VARCHAR(16)   DEFAULT 'png' COMMENT '图片格式: 统一png(强制转码后保存)',
    `image_width`          INT           DEFAULT NULL COMMENT '图片宽',
    `image_height`         INT           DEFAULT NULL COMMENT '图片高',
    `image_url_origin`     VARCHAR(1024) DEFAULT NULL COMMENT '知眸回调时原始image_url(如果有的话)',
    `image_has_osd`        TINYINT       DEFAULT 1 COMMENT '图片是否包含AI标注框: 0-原图 1-带OSD标注',

    -- ------------------ 识别命中信息 ------------------
    `rule_id`              VARCHAR(128)  DEFAULT NULL COMMENT '命中规则ID(回调里的rules[].rule_id)',
    `rule_name`            VARCHAR(128)  DEFAULT NULL COMMENT '命中规则名称(回调里的rules[].rule_name)',
    `hit_count`            INT           NOT NULL DEFAULT 0 COMMENT '本次回调总共命中的目标数量(rules[].targets总长度)',
    `targets_json`         JSON          DEFAULT NULL COMMENT '完整目标明细JSON(规则数组/目标框数组/置信度/类别等,前端点详情直接渲染)',

    -- ------------------ 飞机实时位置(GPS): 前端地图打点用 ------------------
    `latitude`             DECIMAL(12,9) DEFAULT NULL COMMENT '纬度(来自回调flight.latitude 或 同期上报的OSD)',
    `longitude`            DECIMAL(12,9) DEFAULT NULL COMMENT '经度',
    `altitude`             DECIMAL(10,2) DEFAULT NULL COMMENT '海拔高度(米)',
    `height`               DECIMAL(10,2) DEFAULT NULL COMMENT '相对高度(米)',
    `gimbal_pitch`         DECIMAL(8,2)  DEFAULT NULL COMMENT '云台俯仰角',
    `zoom_factor`          DECIMAL(6,2)  DEFAULT NULL COMMENT '相机变焦倍数',

    -- ------------------ 时间 ------------------
    `event_timestamp_ms`   BIGINT        NOT NULL COMMENT '事件时间戳(毫秒, 知眸回调里的timestamp)',
    `event_time`           DATETIME      NOT NULL COMMENT '事件时间(event_timestamp_ms格式化, 方便SQL时间范围查询)',

    `raw_callback_json`    JSON          DEFAULT NULL COMMENT '知眸回调原始JSON完整内容(存一份方便排查)',
    `work_order_id`        BIGINT UNSIGNED DEFAULT NULL COMMENT '关联工单ID(后续生成工单时回填)',

    `deleted`              TINYINT       NOT NULL DEFAULT 0,
    `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_zhimou_task_id` (`zhimou_task_id`),
    KEY `idx_aircraft_sn` (`aircraft_sn`),
    KEY `idx_event_time` (`event_time`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_live_stream_id` (`live_stream_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知眸AI识别结果明细表(图片+GPS+目标+时间)';

-- -----------------------------------------------------------
-- 12. 工单主表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `work_order`;
CREATE TABLE `work_order` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`                 VARCHAR(32)    NOT NULL COMMENT '工单编号(如WO-20240110-001)',
    `alarm_id`             BIGINT UNSIGNED NOT NULL COMMENT '关联告警ID(zhimou_detection_result.id)',
    `alarm_type`           VARCHAR(100)   DEFAULT NULL COMMENT '告警类型',
    `aircraft_sn`          VARCHAR(100)   DEFAULT NULL COMMENT '飞机SN',
    `aircraft_name`        VARCHAR(128)   DEFAULT NULL COMMENT '飞机名称',
    `wayline_uuid`         VARCHAR(64)    DEFAULT NULL COMMENT '航线UUID',
    `wayline_name`         VARCHAR(128)   DEFAULT NULL COMMENT '航线名称',
    `alarm_image_path`     VARCHAR(500)   DEFAULT NULL COMMENT '告警图片路径(复用已有路径)',
    `handler_email`        VARCHAR(100)   DEFAULT NULL COMMENT '处理人邮箱',
    `handler_name`         VARCHAR(50)    DEFAULT NULL COMMENT '处理人姓名',
    `status`               VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING-待处理 PROCESSING-处理中 SUBMITTED-已提交 COMPLETED-已完成 REJECTED-已驳回',
    `handle_comment`       TEXT           DEFAULT NULL COMMENT '处理意见',
    `reject_reason`        TEXT           DEFAULT NULL COMMENT '驳回原因',
    `cc_email`             VARCHAR(100)   DEFAULT NULL COMMENT '抄送邮箱',
    `h5_token`             VARCHAR(64)    DEFAULT NULL COMMENT 'H5访问Token',
    `latitude`             DECIMAL(12,9)  DEFAULT NULL COMMENT '告警位置纬度',
    `longitude`            DECIMAL(12,9)  DEFAULT NULL COMMENT '告警位置经度',
    `event_time`           DATETIME       DEFAULT NULL COMMENT '告警时间',
    `submit_time`          DATETIME       DEFAULT NULL COMMENT '提交时间',
    `complete_time`        DATETIME       DEFAULT NULL COMMENT '完成时间',
    `reject_time`          DATETIME       DEFAULT NULL COMMENT '驳回时间',
    `deleted`              TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除:0-未删 1-已删',
    `create_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    UNIQUE KEY `uk_alarm_id` (`alarm_id`),
    UNIQUE KEY `uk_h5_token` (`h5_token`),
    KEY `idx_status` (`status`),
    KEY `idx_aircraft_sn` (`aircraft_sn`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单主表';

-- -----------------------------------------------------------
-- 13. 工单图片表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `work_order_image`;
CREATE TABLE `work_order_image` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `work_order_id`        BIGINT UNSIGNED NOT NULL COMMENT '关联工单ID',
    `image_path`           VARCHAR(500)   NOT NULL COMMENT '图片本地路径',
    `image_type`           VARCHAR(20)    NOT NULL DEFAULT 'HANDLER' COMMENT '图片类型:ALARM-原始告警 HANDLER-处理上传',
    `create_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_work_order_id` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单图片表';
