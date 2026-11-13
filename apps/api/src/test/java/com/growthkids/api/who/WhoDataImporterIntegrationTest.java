package com.growthkids.api.who;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.growthkids.api.who.entity.WhoGrowthStandard;
import com.growthkids.api.who.importer.WhoDataException;
import com.growthkids.api.who.importer.WhoDataImporter;
import com.growthkids.api.who.mapper.WhoGrowthStandardMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * WHO 数据导入管线集成测试（TASK-008）：启动 seed、幂等导入、最近锚点查询、非法数据不落库。
 *
 * <p>内置示例数据（合成，非官方）：source=growthkids-example-synthetic，version=2026.1-example，
 * 0-60 月每 3 月一个锚点 × 4 指标 × 2 性别 = 168 行。
 */
@SpringBootTest
@ActiveProfiles("test")
class WhoDataImporterIntegrationTest {

    private static final String EXAMPLE_SOURCE = "growthkids-example-synthetic";
    private static final String EXAMPLE_VERSION = "2026.1-example";

    @Autowired private WhoDataImporter importer;
    @Autowired private WhoGrowthStandardMapper whoGrowthStandardMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanImportedTestData() {
        cleanImportedSources();
    }

    @AfterEach
    void cleanAfterTest() {
        // 避免测试数据集残留影响其他套件的 active dataset 解析（findLatestImported）
        cleanImportedSources();
    }

    private void cleanImportedSources() {
        jdbcTemplate.update(
                "DELETE FROM who_growth_standards WHERE source IN ('it-who-source', 'it-who-bad')");
    }

    @Test
    void seederImportedExampleDataOnStartup() {
        // 应用启动时 WhoDataSeeder 自动导入内置示例数据（幂等）
        assertEquals(168L, whoGrowthStandardMapper.countByVersion(EXAMPLE_SOURCE, EXAMPLE_VERSION));
    }

    @Test
    void findLatestUpToUsesNearestAnchor() {
        // 100 天落在 [91, 183) 区间 → 取 91 天锚点（§35-4：不晚于 ageDays 的最近锚点）
        WhoGrowthStandard anchor = find(100);
        assertNotNull(anchor);
        assertEquals(91, anchor.getAgeDays());
        assertEquals(0, new BigDecimal("60.1753").compareTo(anchor.getMValue()));

        // 恰好命中锚点
        assertEquals(91, find(91).getAgeDays());
        // 低于首个锚点 → 取 0 天锚点
        assertEquals(0, find(90).getAgeDays());
        assertEquals(0, new BigDecimal("49.9").compareTo(find(0).getMValue()));
        // 超出最大锚点 → 取末个锚点
        assertEquals(1826, find(100000).getAgeDays());
        assertEquals(0, new BigDecimal("108.5866").compareTo(find(100000).getMValue()));
    }

    @Test
    void importIsIdempotent() {
        ClassPathResource testData = new ClassPathResource("who-data/who-standards-test.csv");
        assertEquals(5, importer.importFrom(testData));
        assertEquals(5L, whoGrowthStandardMapper.countByVersion("it-who-source", "1.0"));
        // 同 source+version 二次导入 → 跳过返回 0，不产生重复行
        assertEquals(0, importer.importFrom(testData));
        assertEquals(5L, whoGrowthStandardMapper.countByVersion("it-who-source", "1.0"));
    }

    @Test
    void importRejectsInvalidDataWithoutPartialWrites() {
        String invalid =
                """
                # source: it-who-bad
                # version: 1.0
                sex,indicator,age_days,l_value,m_value,s_value
                male,height_for_age,0,1.0,50.0,0.04
                male,height_for_age,0,1.0,51.0,0.04
                """;
        ByteArrayResource resource = new ByteArrayResource(invalid.getBytes(StandardCharsets.UTF_8));
        assertThrows(WhoDataException.class, () -> importer.importFrom(resource));
        // 校验先于写入：失败后库中无任何部分数据（§35-7）
        assertEquals(0L, whoGrowthStandardMapper.countByVersion("it-who-bad", "1.0"));
    }

    private WhoGrowthStandard find(int ageDays) {
        return whoGrowthStandardMapper.findLatestUpTo(
                EXAMPLE_SOURCE, EXAMPLE_VERSION, "male", "height_for_age", ageDays);
    }
}

