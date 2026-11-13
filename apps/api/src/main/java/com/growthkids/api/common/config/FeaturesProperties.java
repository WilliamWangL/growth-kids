package com.growthkids.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 功能开关（app.features.*，application.yml 预置段；TASK-016 起消费 enable-membership）。 */
@ConfigurationProperties(prefix = "app.features")
public class FeaturesProperties {

    private boolean enableAi;

    private boolean enableReport = true;

    private boolean enableDonation = true;

    private boolean enableMembership = true;

    private boolean enableShare;

    public boolean isEnableAi() {
        return enableAi;
    }

    public void setEnableAi(boolean enableAi) {
        this.enableAi = enableAi;
    }

    public boolean isEnableReport() {
        return enableReport;
    }

    public void setEnableReport(boolean enableReport) {
        this.enableReport = enableReport;
    }

    public boolean isEnableDonation() {
        return enableDonation;
    }

    public void setEnableDonation(boolean enableDonation) {
        this.enableDonation = enableDonation;
    }

    public boolean isEnableMembership() {
        return enableMembership;
    }

    public void setEnableMembership(boolean enableMembership) {
        this.enableMembership = enableMembership;
    }

    public boolean isEnableShare() {
        return enableShare;
    }

    public void setEnableShare(boolean enableShare) {
        this.enableShare = enableShare;
    }
}

