package com.growthkids.api.user.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 用户相关配置（app.user.*）。第一版支持语言见规范 §126：zh-CN / en-US。 */
@ConfigurationProperties(prefix = "app.user")
public class UserProperties {

    private List<String> supportedLocales = List.of("en-US", "zh-CN");

    public List<String> getSupportedLocales() {
        return supportedLocales;
    }

    public void setSupportedLocales(List<String> supportedLocales) {
        this.supportedLocales = supportedLocales;
    }
}
