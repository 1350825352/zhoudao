package com.dji.sikong.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dji.sikong.common.R;
import com.dji.sikong.entity.User;
import com.dji.sikong.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/list")
    public R<IPage<User>> list(@RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int size) {
        try {
            Page<User> pageParam = new Page<>(page, size);
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getDeleted, 0);
            wrapper.orderByDesc(User::getCreateTime);
            IPage<User> userPage = userService.page(pageParam, wrapper);
            return R.ok(userPage);
        } catch (Exception e) {
            log.error("[用户管理] 查询用户列表失败", e);
            return R.fail("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public R<User> getById(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return R.fail("用户不存在");
            }
            return R.ok(user);
        } catch (Exception e) {
            log.error("[用户管理] 查询用户失败", e);
            return R.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping
    public R<User> create(@RequestBody User user) {
        try {
            userService.save(user);
            log.info("[用户管理] 创建用户成功, username={}", user.getUsername());
            return R.ok("创建成功", user);
        } catch (Exception e) {
            log.error("[用户管理] 创建用户失败", e);
            return R.fail("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public R<User> update(@PathVariable Long id, @RequestBody User user) {
        try {
            user.setId(id);
            userService.updateById(user);
            log.info("[用户管理] 更新用户成功, id={}", id);
            return R.ok("更新成功", user);
        } catch (Exception e) {
            log.error("[用户管理] 更新用户失败", e);
            return R.fail("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        try {
            userService.removeById(id);
            log.info("[用户管理] 删除用户成功, id={}", id);
            return R.ok("删除成功", null);
        } catch (Exception e) {
            log.error("[用户管理] 删除用户失败", e);
            return R.fail("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public R<List<User>> getAll() {
        try {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getDeleted, 0);
            List<User> list = userService.list(wrapper);
            return R.ok(list);
        } catch (Exception e) {
            log.error("[用户管理] 查询所有用户失败", e);
            return R.fail("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id) {
        try {
            User user = userService.getById(id);
            if (user == null) {
                return R.fail("用户不存在");
            }
            user.setPassword("21218cca77804d2ba1922c33e0151105");
            userService.updateById(user);
            log.info("[用户管理] 重置密码成功, id={}, username={}", id, user.getUsername());
            return R.ok("密码已重置为888888", null);
        } catch (Exception e) {
            log.error("[用户管理] 重置密码失败", e);
            return R.fail("重置失败: " + e.getMessage());
        }
    }
}