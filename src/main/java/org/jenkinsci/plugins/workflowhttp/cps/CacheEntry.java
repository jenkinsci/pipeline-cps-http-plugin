package org.jenkinsci.plugins.workflowhttp.cps;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class CacheEntry {
    public static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    public final Instant expirationDate;
    public final String script;

    public CacheEntry(Instant expirationDate, String script) {
        this.expirationDate = expirationDate;
        this.script = script;
    }
}
