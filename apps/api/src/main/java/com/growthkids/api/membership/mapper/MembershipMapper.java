package com.growthkids.api.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.membership.entity.Membership;
import org.apache.ibatis.annotations.Mapper;

/** memberships 读写（TASK-016，§18）。 */
@Mapper
public interface MembershipMapper extends BaseMapper<Membership> {}

