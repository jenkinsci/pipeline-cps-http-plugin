package org.jenkinsci.plugins.workflowhttp.cps;

import org.kohsuke.stapler.DataBoundConstructor;

public class CachingConfiguration {
    private int cachingMinutes;

    @DataBoundConstructor public CachingConfiguration(int cachingMinutes) {
        this.cachingMinutes = cachingMinutes;
    }

    public int getCachingMinutes() {
        return cachingMinutes;
    }

    public int getCachingSeconds() {
        return cachingMinutes * 60;
    }
}