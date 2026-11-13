package com.growthkids.api.who.importer;

import com.growthkids.api.who.config.WhoDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 启动时自动导入 WHO 数据（app.who.auto-import=true 时启用，幂等）。
 *
 * <p>数据文件由 app.who.data-file 指定（默认内置示例数据，见 data/who/README.md 替换为官方数据）。
 * 数据库已有同 source+version 数据时静默跳过；文件缺失/非法时快速失败，阻止带着不完整增长标准启动。
 */
@Component
public class WhoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WhoDataSeeder.class);

    private final WhoDataProperties properties;
    private final WhoDataImporter importer;
    private final ResourceLoader resourceLoader;

    public WhoDataSeeder(
            WhoDataProperties properties, WhoDataImporter importer, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.importer = importer;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoImport()) {
            log.info("WHO data auto-import disabled (app.who.auto-import=false)");
            return;
        }
        Resource resource = resourceLoader.getResource(properties.getDataFile());
        if (!resource.exists()) {
            throw new WhoDataException("WHO data file not found: " + properties.getDataFile());
        }
        importer.importFrom(resource);
    }
}
