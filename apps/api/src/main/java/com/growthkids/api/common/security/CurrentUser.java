package com.growthkids.api.common.security;

import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 读取当前认证用户（供 Service 层做 ownership 校验，规范 §70）。 */
public final class CurrentUser {

    private CurrentUser() {}

    /** 必须已认证，否则抛出 AUTH_UNAUTHORIZED。 */
    public static AuthPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
        }
        return principal;
    }

    public static long id() {
        return require().userId();
    }

    public static boolean isAdmin() {
        return require().isAdmin();
    }
}
