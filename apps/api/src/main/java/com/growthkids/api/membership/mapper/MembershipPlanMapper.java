package com.growthkids.api.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.membership.entity.MembershipPlan;
import org.apache.ibatis.annotations.Mapper;

/** membership_plans 读写（TASK-016，§64 价格配置化）。 */
@Mapper
public interface MembershipPlanMapper extends BaseMapper<MembershipPlan> {}

