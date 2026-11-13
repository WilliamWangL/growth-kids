package com.growthkids.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class GrowthEngineTest {

    @Test
    void engineExposesVersion() {
        assertNotNull(GrowthEngine.VERSION);
    }
}
