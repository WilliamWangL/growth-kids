package com.growthkids.api.photo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.photo.entity.GrowthPhoto;
import org.apache.ibatis.annotations.Mapper;

/** growth_photos 表访问（TASK-013）。 */
@Mapper
public interface GrowthPhotoMapper extends BaseMapper<GrowthPhoto> {}

