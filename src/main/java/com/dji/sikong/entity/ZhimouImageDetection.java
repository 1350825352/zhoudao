package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知眸图片识别结果表(对应 zhimou_image_detection)
 * <p>
 * 数据来源: 知眸图片任务异步回调 (POST /api/zhimou/callback)
 * 与一期视频表 zhimou_detection_result 完全独立, 通过 zhimouTask.mode="image" 分流
 * 通过 local_image_name 与 self_model_image_detection 表关联做数据清洗
 */
@Data
@TableName("zhimou_image_detection")
public class ZhimouImageDetection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // ================ 知眸任务关联 ================
    /** 知眸task_id(创建任务时返回) */
    private String zhimouTaskId;

    /** 业务ID(创建任务时我方传入, 本地测试阶段用 local_image_name) */
    private String businessId;

    /** 知眸应用包ID */
    private String zapkId;

    /** 命中的应用ID(单个图片回调通常1个app) */
    private String appId;

    /** 应用名称(如:小客车识别/人员识别) */
    private String appName;

    /** 设备ID(飞机SN) */
    private String deviceId;

    // ================ 图片信息(数据清洗关联键) ================
    /** 本地保存的图片文件名(如DJI_20260806165701_0001_V), 数据清洗关联键 */
    private String localImageName;

    /** 知眸回调里frame.image原始路径 */
    private String zhimouImagePath;

    /** 知眸app叠加标注后图片路径 */
    private String zhimouAppImage;

    /** 知眸标注图本地落盘相对路径(yyyyMMdd/{taskUuid}/annotated/xxx.png), 命中时才有值 */
    private String annotatedLocalPath;

    /** 本地落盘的相对路径(年月日/文件名.png) */
    private String localImagePath;

    /** 图片宽度(像素) */
    private Integer imageWidth;

    /** 图片高度(像素) */
    private Integer imageHeight;

    /** 帧ID(图片模式下一般为0) */
    private Long frameId;

    /** frame.enhance增强参数 */
    private Integer frameEnhance;

    // ================ 图片EXIF GPS信息 ================
    /** 纬度(EXIF GPS, 正数北纬 负数南纬) */
    private BigDecimal latitude;

    /** 经度(EXIF GPS, 正数东经 负数西经) */
    private BigDecimal longitude;

    /** 海拔高度(米, EXIF GPSAltitude) */
    private BigDecimal altitude;

    // ================ 识别结果 ================
    /** 本次回调命中目标总数 */
    private Integer hitCount;

    /** objects数组原文JSON [{class_id,name,color,x,y,track_id,confidence,description}] */
    private String targetsJson;

    // ================ 时间 ================
    /** 知眸回调timestamp(毫秒) */
    private Long eventTimestampMs;

    /** 事件时间(由timestamp转) */
    private LocalDateTime eventTime;

    /** 收到回调的时间 */
    private LocalDateTime receivedTime;

    // ================ 原始数据 ================
    /** 完整回调JSON存档(排错/审计用) */
    private String rawCallbackJson;

    /** 回调事件类型: start/message/stop */
    private String callbackEvent;

    // ================ 数据清洗标记 ================
    /** 清洗状态: 0未清洗 1已清洗 2判定为误识别 */
    private Integer cleanStatus;

    /** 清洗时间 */
    private LocalDateTime cleanTime;

    /** 清洗备注 */
    private String cleanRemark;

    // ================ 工单关联 ================
    /** 关联工单ID */
    private Long workOrderId;

    // ================ 标准字段 ================
    /** 逻辑删除 */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
