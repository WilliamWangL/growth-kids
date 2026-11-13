package com.growthkids.api.membership;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 会员配置（app.membership.*，规范 §64/§78 价格与限额配置化）。 */
@ConfigurationProperties(prefix = "app.membership")
public class MembershipProperties {

    /** Free 套餐孩子数量上限。 */
    private int freeChildLimit = 1;

    /** Free 套餐照片数量上限。 */
    private int freePhotoLimit = 100;

    /** Free 套餐时间线条数上限。 */
    private int freeTimelineLimit = 20;

    public int getFreeChildLimit() {
        return freeChildLimit;
    }

    public void setFreeChildLimit(int freeChildLimit) {
        this.freeChildLimit = freeChildLimit;
    }

    public int getFreePhotoLimit() {
        return freePhotoLimit;
    }

    public void setFreePhotoLimit(int freePhotoLimit) {
        this.freePhotoLimit = freePhotoLimit;
    }

    public int getFreeTimelineLimit() {
        return freeTimelineLimit;
    }

    public void setFreeTimelineLimit(int freeTimelineLimit) {
        this.freeTimelineLimit = freeTimelineLimit;
    }
}
