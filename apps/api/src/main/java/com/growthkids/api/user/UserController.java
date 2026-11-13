package com.growthkids.api.user;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.user.dto.UpdateProfileRequest;
import com.growthkids.api.user.dto.UserProfileResponse;
import com.growthkids.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户资料 API（TASK-005）：profile / update profile / timezone / locale。 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "用户资料")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户资料", description = "返回当前登录用户的资料（含 timezone / locale）")
    public UserProfileResponse me() {
        return userService.getProfile(CurrentUser.id());
    }

    @PatchMapping("/me")
    @Operation(
            summary = "更新用户资料",
            description = "更新 displayName / avatarUrl / timezone / locale；字段缺省保持不变，空字符串清空（可空字段）")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(CurrentUser.id(), request);
    }
}
