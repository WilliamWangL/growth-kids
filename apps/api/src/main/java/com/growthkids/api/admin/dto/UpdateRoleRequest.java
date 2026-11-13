package com.growthkids.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 变更用户角色（§77）：仅允许 user/admin 两值，其他值走 400。 */
public record UpdateRoleRequest(
        @NotBlank @Pattern(regexp = "user|admin", message = "must be one of: user, admin") String role) {}
