package com.growthkids.api.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS 配置（app.cors.*）。默认放行本地 Web dev 与 Nginx 网关。 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins =
            List.of("http://localhost:3005", "http://localhost:8090");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
