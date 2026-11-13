package com.growthkids.api.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.growthkids.api.common.security.PasswordHasher;
import com.growthkids.api.user.entity.User;
import com.growthkids.api.user.mapper.UserMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 管理员引导（TASK-019，§77）：环境无 admin 时按配置创建首个管理员。
 * 仅在配置了 app.admin.email/password 且该邮箱尚无用户时执行；
 * 邮箱已存在（含已是 admin）不改动，避免配置误伤既有账户。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;

    private final UserMapper userMapper;

    private final PasswordHasher passwordHasher;

    public AdminBootstrap(AdminProperties properties, UserMapper userMapper, PasswordHasher passwordHasher) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.getEmail();
        String password = properties.getPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email.toLowerCase()));
        if (existing != null && existing > 0) {
            return;
        }
        User admin = new User();
        admin.setEmail(email.toLowerCase());
        admin.setPasswordHash(passwordHasher.hash(password));
        admin.setDisplayName("Admin");
        admin.setTimezone("UTC");
        admin.setLocale("en-US");
        admin.setStatus(User.STATUS_ACTIVE);
        admin.setRole(User.ROLE_ADMIN);
        admin.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        userMapper.insert(admin);
        log.info("Bootstrap admin created: {}", admin.getEmail());
    }
}
