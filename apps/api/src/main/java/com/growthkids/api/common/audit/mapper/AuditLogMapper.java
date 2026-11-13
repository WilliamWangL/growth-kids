package com.growthkids.api.common.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.common.audit.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {}
