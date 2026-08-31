package com.dji.sikong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dji.sikong.common.R;
import com.dji.sikong.entity.User;
import com.dji.sikong.service.UserService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_PREFIX = "sikong:token:";

    @GetMapping("/verify")
    public R<Map<String, Object>> verify(@RequestParam("token") String token) {
        try {
            log.info("[认证] 收到token验证请求, token={}", token);

            String key = TOKEN_PREFIX + token;
            Object userObj = redisTemplate.opsForValue().get(key);

            if (userObj == null) {
                return R.fail(401, "登录已过期，请重新登录");
            }

            Map<String, Object> userInfo = (Map<String, Object>) userObj;
            Boolean hasAdminAccess = (Boolean) userInfo.get("hasAdminAccess");

            log.info("[认证] token验证, username={}, dept={}, hasAdminAccess={}",
                    userInfo.get("username"), userInfo.get("dept"), hasAdminAccess);

            // 权限校验：只有信息中心和项目管理部门的人员能登录管理后台
            if (hasAdminAccess == null || !hasAdminAccess) {
                return R.fail(403, "您没有权限登录管理后台，请联系管理员");
            }

            // 滑动过期：每次校验成功重置2小时TTL，活跃用户不会被强制重新登录
            redisTemplate.expire(TOKEN_PREFIX + token, 2, TimeUnit.HOURS);

            Map<String, Object> result = new HashMap<>();
            result.put("id", userInfo.get("id"));
            result.put("username", userInfo.get("username"));
            result.put("name", userInfo.get("nickname"));
            result.put("dept", userInfo.get("dept"));

            return R.ok(result);
        } catch (Exception e) {
            log.error("[认证] token验证失败, error={}", e.getMessage(), e);
            return R.fail(500, "验证失败");
        }
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        try {
            log.info("[认证] 收到登录请求, loginData={}", loginData);
            String username = loginData.get("username");
            String password = loginData.get("password");

            log.info("[认证] username={}, password_length={}", username, password != null ? password.length() : 0);

            if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
                return R.fail(400, "用户名或密码不能为空");
            }

            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username);
            wrapper.eq(User::getDeleted, 0);
            User user = userService.getOne(wrapper);

            log.info("[认证] 查询到用户: {}", user);

            if (user == null) {
                return R.fail(401, "用户名不存在");
            }

            String encryptedPassword = md5Encrypt(password);
            log.info("[认证] 输入密码MD5={}, 数据库密码={}", encryptedPassword, user.getPassword());

            if (!encryptedPassword.equals(user.getPassword())) {
                return R.fail(401, "密码错误");
            }

            if (user.getStatus() != null && user.getStatus() != 1) {
                return R.fail(401, "用户已禁用");
            }

            String token = UUID.randomUUID().toString().replace("-", "");

            // 判断是否有权限登录管理后台
            String dept = user.getDept();
            boolean hasAdminAccess = dept != null && (dept.contains("信息中心") || dept.contains("项目管理") || dept.contains("项目管理部门"));

            // 将用户信息存储到Redis，过期时间2小时
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("nickname", user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername());
            userInfo.put("dept", dept);
            userInfo.put("token", token);
            userInfo.put("hasAdminAccess", hasAdminAccess);

            redisTemplate.opsForValue().set(TOKEN_PREFIX + token, userInfo, 2, TimeUnit.HOURS);

            Map<String, Object> result = new HashMap<>();
            result.put("id", user.getId());
            result.put("username", user.getUsername());
            result.put("name", user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername());
            result.put("token", token);
            result.put("dept", dept);
            result.put("hasAdminAccess", hasAdminAccess);

            log.info("[认证] 用户登录成功, username={}", username);
            return R.ok(result);
        } catch (Exception e) {
            log.error("[认证] 登录失败, error={}", e.getMessage(), e);
            return R.fail(500, "登录失败: " + e.getMessage());
        }
    }

    private String md5Encrypt(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5加密失败", e);
        }
    }
}
