package com.growthkids.api.who.importer;

import com.growthkids.api.who.entity.WhoGrowthStandard;
import com.growthkids.api.who.mapper.WhoGrowthStandardMapper;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WHO 数据导入器（TASK-008，规范 §35）。
 *
 * <p>保证：
 *
 * <ul>
 *   <li>幂等：同 source+version 已存在则跳过（换版本时 bump version，旧数据保留）
 *   <li>事务性：校验通过后事务内写入，中途失败全部回滚，不产生部分数据
 *   <li>先校验后写入：parser（格式）+ validator（数据级）均通过才落库（§35-7）
 * </ul>
 */
@Service
public class WhoDataImporter {

    private static final Logger log = LoggerFactory.getLogger(WhoDataImporter.class);

    private final WhoGrowthStandardMapper whoGrowthStandardMapper;
    private final WhoCsvParser parser;
    private final WhoDataValidator validator;

    public WhoDataImporter(
            WhoGrowthStandardMapper whoGrowthStandardMapper,
            WhoCsvParser parser,
            WhoDataValidator validator) {
        this.whoGrowthStandardMapper = whoGrowthStandardMapper;
        this.parser = parser;
        this.validator = validator;
    }

    /**
     * 从资源导入 WHO 数据。
     *
     * @return 导入行数；同 source+version 已存在时返回 0（跳过）
     */
    @Transactional
    public int importFrom(Resource resource) {
        WhoDataSet dataSet;
        try (InputStream in = resource.getInputStream()) {
            dataSet = parser.parse(in);
        } catch (IOException e) {
            throw new WhoDataException(
                    "cannot open WHO data resource " + resource.getDescription() + ": " + e.getMessage(),
                    e);
        }

        long existing = whoGrowthStandardMapper.countByVersion(dataSet.source(), dataSet.version());
        if (existing > 0) {
            log.info(
                    "WHO data {}@{} already imported ({} rows), skipping",
                    dataSet.source(),
                    dataSet.version(),
                    existing);
            return 0;
        }

        validator.validate(dataSet);

        long start = System.currentTimeMillis();
        for (ParsedLmsRow row : dataSet.rows()) {
            whoGrowthStandardMapper.insert(toEntity(dataSet.source(), dataSet.version(), row));
        }
        log.info(
                "Imported {} WHO LMS rows ({}@{}) in {} ms",
                dataSet.size(),
                dataSet.source(),
                dataSet.version(),
                System.currentTimeMillis() - start);
        return dataSet.size();
    }

    private static WhoGrowthStandard toEntity(
            String source, String version, ParsedLmsRow row) {
        WhoGrowthStandard entity = new WhoGrowthStandard();
        entity.setSource(source);
        entity.setVersion(version);
        entity.setSex(row.sex());
        entity.setIndicator(row.indicator().code());
        entity.setAgeDays(row.ageDays());
        entity.setLValue(row.l());
        entity.setMValue(row.m());
        entity.setSValue(row.s());
        return entity;
    }
}
