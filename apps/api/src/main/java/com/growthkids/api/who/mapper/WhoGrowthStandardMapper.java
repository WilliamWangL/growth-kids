package com.growthkids.api.who.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.who.entity.WhoGrowthStandard;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** who_growth_standards 表 Mapper（只读使用，规范 §11）。 */
public interface WhoGrowthStandardMapper extends BaseMapper<WhoGrowthStandard> {

    /**
     * 查询不晚于 ageDays 的最近锚点（规范 §35-3/§35-4）。
     *
     * <p>WHO 数据若为离散年龄点（按月/按周），官方允许的方法是取所属年龄区间锚点，
     * 不允许随意线性插值（§35-5）；本方法即该语义的实现基础。
     */
    @Select(
            "SELECT * FROM who_growth_standards"
                    + " WHERE source = #{source} AND version = #{version}"
                    + " AND sex = #{sex} AND indicator = #{indicator} AND age_days <= #{ageDays}"
                    + " ORDER BY age_days DESC LIMIT 1")
    WhoGrowthStandard findLatestUpTo(
            @Param("source") String source,
            @Param("version") String version,
            @Param("sex") String sex,
            @Param("indicator") String indicator,
            @Param("ageDays") int ageDays);

    /** 指定 source+version 的已导入行数（幂等检查，规范 §35-6）。 */
    @Select(
            "SELECT COUNT(*) FROM who_growth_standards WHERE source = #{source} AND version = #{version}")
    long countByVersion(@Param("source") String source, @Param("version") String version);

    /**
     * 最近一次导入的数据集（active dataset）：成长记录计算 WHO 指标时使用（规范 §35-6）。
     *
     * <p>最新导入批次的 created_at 最大，取一行即可定位其 source/version；
     * 更换 WHO 数据版本后无需额外配置即可切换到新版本。
     */
    @Select("SELECT * FROM who_growth_standards ORDER BY created_at DESC, id DESC LIMIT 1")
    WhoGrowthStandard findLatestImported();
}
