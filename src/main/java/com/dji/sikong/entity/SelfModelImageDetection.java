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
 * 自有模型图片识别结果表(对应 self_model_image_detection)
 * <p>
 * 数据来源: 自有识别模型同步API (POST http://192.168.2.200:5173/api/v1/detect)
 * 与知眸图片识别表完全独立, 不做关联
 * 通过 local_image_name 与 zhimou_image_detection 表关联做数据清洗
 */
@Data
@TableName("self_model_image_detection")
public class SelfModelImageDetection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    // ================ 自有模型标识 ================
    /** 自有模型返回的request_id(req_时间戳_hex, 由服务器生成) */
    private String requestId;

    /** 模型版本(如 mingp) */
    private String modelVersion;

    /** 推理设备(cpu/cuda:0) */
    private String modelDevice;

    /** 服务器地址(http://192.168.2.200:5173) */
    private String serverUrl;

    // ================ 图片信息(数据清洗关联键) ================
    /** 本地图片文件名(上传时的原始文件名, 数据清洗关联键, 与知眸表用同一个值) */
    private String localImageName;

    /** 本地落盘的相对路径(年月日/文件名.png, 与知眸表用同一个路径) */
    private String localImagePath;

    /** 自有模型返回的image_path(data/images/req_xxx.png) */
    private String serverImagePath;

    /** 图片宽度(像素) */
    private Integer imageWidth;

    /** 图片高度(像素) */
    private Integer imageHeight;

    /** 图片格式(png/jpg) */
    private String imageFormat;

    // ================ 图片EXIF GPS信息 ================
    /** 纬度(EXIF GPS, 正数北纬 负数南纬) */
    private BigDecimal latitude;

    /** 经度(EXIF GPS, 正数东经 负数西经) */
    private BigDecimal longitude;

    /** 海拔高度(米, EXIF GPSAltitude) */
    private BigDecimal altitude;

    // ================ 识别结果 ================
    /** 识别到的目标总数(detection_count) */
    private Integer hitCount;

    /** detections数组原文JSON [{id,class_id,class_name,category,confidence,bbox{x1,y1,x2,y2}}] */
    private String targetsJson;

    // ================ 调用参数 ================
    /** 调用时传入的置信度阈值 */
    private BigDecimal threshold;

    /** 服务器处理耗时(毫秒) */
    private Integer processingTimeMs;

    /** 服务器处理时间(ISO格式字符串) */
    private String processedAt;

    /** 我方调用接口的时间 */
    private LocalDateTime callTime;

    // ================ 调用结果状态 ================
    /** 调用状态: 0未知 1成功 2失败(HTTP非200或code!=0) */
    private Integer callStatus;

    /** 调用失败时的错误信息 */
    private String errorMessage;

    // ================ 原始数据 ================
    /** 完整响应JSON存档(排错/审计用) */
    private String rawResponseJson;

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
