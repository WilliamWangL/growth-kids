package com.growthkids.api.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.common.audit.mapper.AuditLogMapper;
import com.growthkids.api.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计日志写入（规范 §71 audit logs）：管理操作等敏感动作必须留痕。
 * 写入失败不阻断业务（审计尽力而为，log.warn 便于排查）。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogMapper auditLogMapper;

    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /** 记录一条审计日志（details 为 JSON 列；请求上下文缺失时 ip/requestId 为空）。 */
    public void record(long userId, String action, String entityType, Long entityId, Map<String, ?> details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setRequestId(RequestIdFilter.currentRequestId());
            entry.setIp(currentIp());
            entry.setDetails(details == null ? null : objectMapper.writeValueAsString(details));
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed action={} entity={}:{}", action, entityType, entityId, e);
        }
    }

    /** X-Forwarded-For 优先（经网关时 remoteAddr 是网关 IP），无请求上下文返回 null。 */
    private String currentIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
