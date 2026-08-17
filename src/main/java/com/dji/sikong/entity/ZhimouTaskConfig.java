package com.dji.sikong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("zhimou_task_config")
public class ZhimouTaskConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("zapk_id")
    private String zapkId;

    @TableField("app_ids")
    private String appIds;

    @TableField("report_url")
    private String reportUrl;

    @TableField("output_url")
    private String outputUrl;

    @TableField("osd_display_name")
    private Integer osdDisplayName;

    @TableField("osd_display_box")
    private Integer osdDisplayBox;

    @TableField("osd_display_track")
    private Integer osdDisplayTrack;

    @TableField("osd_font_size")
    private Integer osdFontSize;

    @TableField("osd_border_size")
    private Integer osdBorderSize;

    @TableField("deleted")
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}