package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据源开关配置表(对应 task_switch_config)
 * 用于集中管理视频任务/图片任务/警情数据源等开关
 */
@Data
@TableName("task_switch_config")
public class TaskSwitchConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 开关键名: video_task / image_task / alert_data_source */
    private String switchKey;

    /** 开关值: video_task/image_task 为 "0"/"1"; alert_data_source 为 "video"/"image" */
    private String value;

    /** 最后修改时间 */
    private LocalDateTime updateTime;

    /** 操作人 */
    private String updater;
}
