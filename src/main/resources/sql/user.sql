-- ============================================================
-- 用户信息表
-- ============================================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    `nickname`        VARCHAR(128) DEFAULT NULL COMMENT '用户昵称',
    `email`           VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`           VARCHAR(32)  DEFAULT NULL COMMENT '电话',
    `role`            VARCHAR(64)  DEFAULT NULL COMMENT '角色',
    `dept`            VARCHAR(128) DEFAULT NULL COMMENT '所属部门',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态: 0-禁用 1-启用',
    `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0-未删 1-已删',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- ============================================================
-- 初始化用户数据
-- 默认密码: 888888 (MD5加密)
-- ============================================================
INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `phone`, `role`, `dept`, `status`) VALUES
('admin', '21218cca77804d2ba1922c33e0151105', '超级管理员', 'admin@example.com', '13800138001', '系统管理员', '信息中心', 1),
('zhangsan', '21218cca77804d2ba1922c33e0151105', '张三', 'zhangsan@example.com', '13800138002', '飞控操作员', '作业一部', 1),
('lisi', '21218cca77804d2ba1922c33e0151105', '李四', 'lisi@example.com', '13800138003', '数据分析师', '数据中心', 1),
('wangwu', '21218cca77804d2ba1922c33e0151105', '王五', 'wangwu@example.com', '13800138004', '项目管理员', '项目管理部', 1),
('zhaoliu', '21218cca77804d2ba1922c33e0151105', '赵六', 'zhaoliu@example.com', '13800138005', '普通用户', '作业二部', 0);