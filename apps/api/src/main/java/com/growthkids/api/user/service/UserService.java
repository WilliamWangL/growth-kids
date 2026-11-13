package com.growthkids.api.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.user.config.UserProperties;
import com.growthkids.api.user.dto.UpdateProfileRequest;
import com.growthkids.api.user.dto.UserProfileResponse;
import com.growthkids.api.user.entity.User;
import com.growthkids.api.user.mapper.UserMapper;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户资料服务（规范 TASK-005：profile / update profile / timezone / locale）。 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserProperties userProperties;

    public UserService(UserMapper userMapper, UserProperties userProperties) {
        this.userMapper = userMapper;
        this.userProperties = userProperties;
    }

    public UserProfileResponse getProfile(long userId) {
        return UserProfileResponse.from(requireUser(userId));
    }

    /** 更新资料：仅更新请求中提供的字段；空字符串清空可空字段。 */
    @Transactional
    public UserProfileResponse updateProfile(long userId, UpdateProfileRequest request) {
        requireUser(userId);
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        boolean changed = false;
        if (request.displayName() != null) {
            update.set(User::getDisplayName, normalizeOptional(request.displayName()));
            changed = true;
        }
        if (request.avatarUrl() != null) {
            update.set(User::getAvatarUrl, normalizeOptional(request.avatarUrl()));
            changed = true;
        }
        if (request.timezone() != null) {
            update.set(User::getTimezone, validateTimezone(request.timezone()));
            changed = true;
        }
        if (request.locale() != null) {
            update.set(User::getLocale, validateLocale(request.locale()));
            changed = true;
        }
        if (changed) {
            userMapper.update(null, update);
        }
        return UserProfileResponse.from(requireUser(userId));
    }

    private User requireUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || User.STATUS_DELETED.equals(user.getStatus())) {
            throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
        }
        return user;
    }

    /** IANA 时区校验（§128 用户时区设置）。 */
    private static String validateTimezone(String raw) {
        String timezone = raw.trim();
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_ERROR,
                    "Invalid timezone",
                    Map.of("timezone", "Unsupported IANA timezone: " + raw));
        }
        return timezone;
    }

    /** 语言白名单校验（§126 第一版 zh-CN / en-US，配置化）。 */
    private String validateLocale(String raw) {
        String locale = raw.trim();
        if (!userProperties.getSupportedLocales().contains(locale)) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_ERROR,
                    "Invalid locale",
                    Map.of("locale", "Supported locales: " + userProperties.getSupportedLocales()));
        }
        return locale;
    }

    private static String normalizeOptional(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
