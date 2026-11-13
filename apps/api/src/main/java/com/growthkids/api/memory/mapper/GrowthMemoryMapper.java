package com.growthkids.api.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.memory.entity.GrowthMemory;
import org.apache.ibatis.annotations.Mapper;

/** growth_memories 读写（TASK-014 时间线读取 / TASK-015 CRUD）。 */
@Mapper
public interface GrowthMemoryMapper extends BaseMapper<GrowthMemory> {}
