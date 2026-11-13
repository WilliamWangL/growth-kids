package com.growthkids.api.growth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.growth.entity.GrowthRecord;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** growth_records 表 Mapper。 */
public interface GrowthRecordMapper extends BaseMapper<GrowthRecord> {

    /**
     * 孩子出生日期变化后批量重算 age_days（规范 §37：基于实际日期）。
     *
     * <p>调用前必须保证所有 record_date >= birthDate。
     */
    @Update(
            "UPDATE growth_records SET age_days = DATEDIFF(record_date, #{birthDate})"
                    + " WHERE child_id = #{childId}")
    int recomputeAgeDays(@Param("childId") long childId, @Param("birthDate") LocalDate birthDate);
}
