package com.growthkids.api.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理员引导配置（TASK-019，§77）：启动时若 app.admin.email 邮箱不存在则创建 admin 账户。
 * email 为空（如 test profile）时禁用；已存在的同邮箱账户不改动角色。
 */
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    private String email;

    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
