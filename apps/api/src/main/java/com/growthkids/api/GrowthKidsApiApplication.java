package com.growthkids.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * GrowthKids REST API 入口。
 *
 * 技术栈：Spring Boot 3 + MyBatis Plus + MySQL 8 + Redis + Flyway。
 * 规范：所有 API 前缀统一 /api/v1（AI_DEVELOPMENT_SPEC §23）。
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class GrowthKidsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrowthKidsApiApplication.class, args);
    }
}
