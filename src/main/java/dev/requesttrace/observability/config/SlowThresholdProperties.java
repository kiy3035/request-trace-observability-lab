package dev.requesttrace.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "observability.slow")
public record SlowThresholdProperties(long httpMs, long serviceMs, long sqlMs) {

    public SlowThresholdProperties {
        if (httpMs < 0 || serviceMs < 0 || sqlMs < 0) {
            throw new IllegalArgumentException("Slow thresholds must not be negative");
        }
    }
}

