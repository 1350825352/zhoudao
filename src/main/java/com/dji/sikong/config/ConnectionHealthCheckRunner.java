package com.dji.sikong.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Properties;

@Slf4j
@Component
public class ConnectionHealthCheckRunner implements ApplicationRunner {

    @Resource
    private DataSource dataSource;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║       🩺 数据库 & Redis 连接健康检测              ║");
        log.info("╚══════════════════════════════════════════════════╝");

        // ============ 1. MySQL ============
        System.out.println();
        log.info("─── MySQL 连接检测 ───");
        try (Connection conn = dataSource.getConnection()) {
            log.info("✅ MySQL连接成功! " +
                    "URL={}, Catalog={}, AutoCommit={}, TransactionIsolation={}",
                    conn.getMetaData().getURL(),
                    conn.getCatalog(),
                    conn.getAutoCommit(),
                    conn.getTransactionIsolation());

            try {
                Integer version = jdbcTemplate.queryForObject(
                        "SELECT VERSION()", Integer.class);
                log.info("✅ MySQL版本: {}", version);
            } catch (Exception verEx) {
                // 有些版本返回字符串
                String verStr = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
                log.info("✅ MySQL版本: {}", verStr);
            }

            // 检查项目数据库是否存在（通过查询表数量）
            try {
                Integer tableCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                        Integer.class);
                log.info("✅ 当前数据库中存在 {} 张表", tableCount);
                if (tableCount == 0) {
                    log.warn("⚠️  警告：数据库表为空！请先执行 src/main/resources/sql/init.sql 初始化表结构");
                } else {
                    // 打印已存在的核心表
                    java.util.List<String> tables = jdbcTemplate.queryForList(
                            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name",
                            String.class);
                    log.info("✅ 已存在的表: {}", tables);
                }
            } catch (Exception e) {
                log.error("❌ 查询数据库表失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("❌❌❌ MySQL连接失败! 错误信息: {}", e.getMessage());
            log.error("   请检查: 1) 192.168.2.200:3306 网络是否可达");
            log.error("           2) 账号 szy02 / 密码 123456 是否正确");
            log.error("           3) 用户 szy02 是否有 dockzhimou 库访问权限");
        }

        // ============ 2. Redis ============
        System.out.println();
        log.info("─── Redis 连接检测 ───");
        try {
            String pong = stringRedisTemplate.execute(
                    (RedisConnection connection) -> new String(connection.ping()));
            log.info("✅ Redis连接成功! PING -> {}", pong);

            // 执行 info server 查看版本等
            Properties info = stringRedisTemplate.execute(
                    (RedisConnection connection) -> connection.info("server"));
            if (info != null) {
                log.info("✅ Redis版本: {}, OS: {}, TCP端口: {}",
                        info.get("redis_version"),
                        info.get("os"),
                        info.get("tcp_port"));
                log.info("✅ Redis模式: {}, 已运行 {} 天",
                        "standalone",
                        info.get("uptime_in_days"));
            }

            // 设置一个测试Key
            String testKey = "sikong:healthcheck:startup";
            String testValue = "OK-" + System.currentTimeMillis();
            stringRedisTemplate.opsForValue().set(testKey, testValue, 60, java.util.concurrent.TimeUnit.SECONDS);
            String got = stringRedisTemplate.opsForValue().get(testKey);
            if (testValue.equals(got)) {
                log.info("✅ Redis读/写/过期时间均正常 (测试Key已写入,60秒自动过期)");
            } else {
                log.error("❌ Redis写入后读取失败! 写入={}, 读取={}", testValue, got);
            }

        } catch (Exception e) {
            log.error("❌❌❌ Redis连接失败! 错误信息: {}", e.getMessage());
            log.error("   请检查: 1) 127.0.0.1:6379 端口是否有Redis服务在运行");
            log.error("           2) 如果Redis设置了密码, 请在application.yml spring.data.redis.password 字段填写");
            log.error("           3) 是否本机被防火墙拦截了6379端口");
            log.error("   🔧 快速启动(如果未安装Redis): ");
            log.error("      Docker: docker run -d -p 6379:6379 redis --appendonly yes");
            log.error("      Windows: 下载 Redis-x64-5.x.msi 双击安装即可 默认无密码 127.0.0.1:6379");
        }

        System.out.println();
        log.info("╔══════════════════════════════════════════════════╗");
        log.info("║       🩺 检测完成，详情见上方日志                 ║");
        log.info("╚══════════════════════════════════════════════════╝");
    }
}
