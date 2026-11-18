package com.growthkids.api.deal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.deal.entity.Deal;
import org.apache.ibatis.annotations.Mapper;

/** deals 表访问（TASK-022）。 */
@Mapper
public interface DealMapper extends BaseMapper<Deal> {}
