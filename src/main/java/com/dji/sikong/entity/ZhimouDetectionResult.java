package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知眸AI识别结果明细表(对应 zhimou_detection_result)
 * 前端显示的 图片+经纬度+报警时间 核心都在这张表
 */
@Data
@TableName("zhimou_detection_result")
public class ZhimouDetectionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知眸任务ID(对应 zhimou_task.zhimou_task_id) */
    private String zhimouTaskId;

    /** 关联zhimou_task.id(方便join) */
    private Long taskId;

    /** 关联直播ID(冗余) */
    private Long liveStreamId;

    private String aircraftSn;

    /** 关联航线UUID(飞机执行航线任务时产生) */
    private String waylineUuid;

    private String businessId;

    /** 帧编号/图片编号(image模式下的id) */
    private Long frameId;

    // ================ 图片信息 ================
    /**
     * 图片相对路径(按年月日分目录): 20260709/1719990000000_abcd.png
     * 前端访问时拼成: /api/zhimou-img/{image_relative_path}
     * 注意:统一存为PNG格式(ImageIO自动转码后写入)
     */
    private String imageRelativePath;

    private String imageFormat;

    private Integer imageWidth;

    private Integer imageHeight;

    /** 知眸回调时原始image_url(如果有的话) */
    private String imageUrlOrigin;

    /** 图片是否包含AI标注框: 0-原图 1-带OSD标注 */
    private Integer imageHasOsd;

    // ================ 识别命中信息 ================
    private String ruleId;

    private String ruleName;

    /** 本次回调总共命中的目标数量 */
    private Integer hitCount;

    /** 完整目标明细JSON,前端点详情直接渲染 */
    private String targetsJson;

    // ================ 飞机实时位置(GPS) ================
    private BigDecimal latitude;

    private BigDecimal longitude;

    private BigDecimal altitude;

    private BigDecimal height;

    private BigDecimal gimbalPitch;

    private BigDecimal zoomFactor;

    // ================ 时间 ================
    /** 事件时间戳(毫秒) */
    private Long eventTimestampMs;

    /** 事件时间(方便SQL时间范围查询) */
    private LocalDateTime eventTime;

    /** 知眸回调原始JSON完整内容(存一份方便排查) */
    private String rawCallbackJson;

    /** 关联工单ID(后续生成工单时回填) */
    private Long workOrderId;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
