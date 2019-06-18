package com.aliware.tianchi.policy;

import com.aliware.tianchi.ThrashConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author guohaoice@gmail.com
 */
public class LargeConfig extends BaseConfig {
    private final int maxConcurrency = 600;
    private final int normalConcurrency = 500;
    private final int minConcurrency = 400;
    private final ThrashConfig warmUp = new ThrashConfig(warmUpInSec + onePeriodInSec, maxConcurrency, normalRTTInMs);
    private final ThrashConfig min = new ThrashConfig(onePeriodInSec, minConcurrency, maxRTTInMs);
    private final ThrashConfig normal = new ThrashConfig(onePeriodInSec, normalConcurrency, normalRTTInMs);
    private final ThrashConfig max = new ThrashConfig(onePeriodInSec, maxConcurrency, minRTTInMs);
    private final List<ThrashConfig> allConfig = Collections.unmodifiableList(Arrays.asList(warmUp, min, normal, max));

    public LargeConfig() {
        super(650, 20890);
    }

    @Override
    public List<ThrashConfig> getConfigs() {
        return allConfig;
    }

    @Override
    protected String getQuota() {
        return "large";
    }
}
