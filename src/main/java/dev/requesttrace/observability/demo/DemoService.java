package dev.requesttrace.observability.demo;

import org.springframework.stereotype.Service;

@Service
public class DemoService {

    public long slowService(long delayMs) {
        try {
            Thread.sleep(delayMs);
            return delayMs;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Slow service demo interrupted", exception);
        }
    }
}

