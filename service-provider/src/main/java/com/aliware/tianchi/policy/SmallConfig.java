package com.aliware.tianchi.policy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.aliware.tianchi.ThrashConfig;

/**
 * @author guohaoice@gmail.com
 */
public class SmallConfig extends BaseConfig {
    private final int maxConcurrency = 180;
    private final int normalCurrency = 160;
    private final int minConcurrency = 120;
    private final ThrashConfig warmUp = new ThrashConfig(warmUpInSec + onePeriodInSec, maxConcurrency, normalRTTInMs);
    private final ThrashConfig config0 = new ThrashConfig(onePeriodInSec, maxConcurrency, minRTTInMs);
    private final ThrashConfig config1 = new ThrashConfig(onePeriodInSec, normalCurrency, normalRTTInMs);
    private final ThrashConfig config2 = new ThrashConfig(onePeriodInSec, minConcurrency, maxRTTInMs);
    private final List<ThrashConfig> allConfig = Collections.unmodifiableList(Arrays.asList(warmUp, config0, config1, config2));

    public SmallConfig() {
        super(200);
    }

    @Override
    public List<ThrashConfig> getConfigs() {
        return allConfig;
    }
}
