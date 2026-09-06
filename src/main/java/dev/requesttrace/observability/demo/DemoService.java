package dev.requesttrace.observability.demo;

import org.springframework.stereotype.Service;

@Service
public class DemoService {

    private final SlowSqlDemoRepository slowSqlDemoRepository;

    public DemoService(SlowSqlDemoRepository slowSqlDemoRepository) {
        this.slowSqlDemoRepository = slowSqlDemoRepository;
    }

    public long slowService(long delayMs) {
        try {
            Thread.sleep(delayMs);
            return delayMs;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Slow service demo interrupted", exception);
        }
    }

    public long slowSql(long delayMs) {
        slowSqlDemoRepository.sleep(delayMs);
        return delayMs;
    }

    public void error() {
        throw new IntentionalDemoException();
    }
}

