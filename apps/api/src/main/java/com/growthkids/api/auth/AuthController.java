package com.growthkids.api.auth;

import com.growthkids.api.auth.dto.AuthResponse;
import com.growthkids.api.auth.dto.LoginRequest;
import com.growthkids.api.auth.dto.LogoutRequest;
import com.growthkids.api.auth.dto.RefreshTokenRequest;
import com.growthkids.api.auth.dto.RegisterRequest;
import com.growthkids.api.auth.service.AuthService;
import com.growthkids.api.common.web.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 认证 API（规范 §24-§27）。Controller 仅处理 HTTP 层，业务逻辑在 AuthService。 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "注册 / 登录 / 刷新 / 登出")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "注册", description = "创建账号并返回 access token 与 refresh token")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "登录成功返回 access token 与 refresh token；连续失败触发 429")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, ClientIpResolver.resolve(httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 token", description = "使用 refresh token 轮换（rotation），旧 token 立即失效")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "登出", description = "撤销 refresh token（幂等）")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }
}
