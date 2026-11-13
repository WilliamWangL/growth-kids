package com.growthkids.api.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ErrorResponse;
import com.growthkids.api.common.security.JwtAuthenticationFilter;
import com.growthkids.api.common.security.JwtService;
import com.growthkids.api.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 安全配置（规范 §71）：无状态 JWT 认证、CORS、统一 401/403 JSON。
 *
 * <p>公开端点：注册/登录/刷新/登出（§24-§27）、健康检查、OpenAPI、
 * 公开身高计算（§75）、照片签名 URL（§46 由 token 自校验）、
 * 套餐列表（§64 营销展示）与支付 webhook（§67 签名自校验）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_POST = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/prediction/estimate",
        "/api/v1/payments/webhook/mock"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper, CorsProperties corsProperties)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.POST, PUBLIC_POST)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/media/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/media/upload")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/membership/plans")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/v1/health",
                                                "/api/docs",
                                                "/api/docs/**",
                                                "/api/swagger-ui/**",
                                                "/api/v1/openapi/**",
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/error")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (request, response, authException) ->
                                                        writeError(response, objectMapper, ApiErrorCode.AUTH_UNAUTHORIZED))
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) ->
                                                        writeError(response, objectMapper, ApiErrorCode.AUTH_FORBIDDEN)))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    static void writeError(HttpServletResponse response, ObjectMapper objectMapper, ApiErrorCode code)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body = ErrorResponse.of(code, null, null, RequestIdFilter.currentRequestId());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
