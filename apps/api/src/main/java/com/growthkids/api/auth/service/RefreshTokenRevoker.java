package com.growthkids.api.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.growthkids.api.auth.entity.RefreshToken;
import com.growthkids.api.auth.mapper.RefreshTokenMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh token 泄露处置：撤销用户全部有效 token。
 *
 * <p>必须使用独立事务（REQUIRES_NEW）：调用方在检测到泄露后会抛出异常使外层事务回滚，
 * 若与调用方共享事务，撤销操作会被一并回滚（规范 §71 rotation 安全语义）。
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenMapper refreshTokenMapper;

    public RefreshTokenRevoker(RefreshTokenMapper refreshTokenMapper) {
        this.refreshTokenMapper = refreshTokenMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId) {
        refreshTokenMapper.update(
                null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .isNull(RefreshToken::getRevokedAt)
                        .set(RefreshToken::getRevokedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }
}
