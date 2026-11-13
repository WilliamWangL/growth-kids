package com.growthkids.api.who.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** WHO 数据导入配置（app.who.*）。 */
@ConfigurationProperties(prefix = "app.who")
public class WhoDataProperties {

    /** 启动时自动导入（幂等）；关闭后需手动导入。 */
    private boolean autoImport = true;

    /**
     * 数据文件位置（Spring Resource 语法）。
     * 默认内置示例数据（source=growthkids-example-synthetic）；生产环境替换为官方数据文件。
     */
    private String dataFile = "classpath:who-data/who-standards-example.csv";

    public boolean isAutoImport() {
        return autoImport;
    }

    public void setAutoImport(boolean autoImport) {
        this.autoImport = autoImport;
    }

    public String getDataFile() {
        return dataFile;
    }

    public void setDataFile(String dataFile) {
        this.dataFile = dataFile;
    }
}
