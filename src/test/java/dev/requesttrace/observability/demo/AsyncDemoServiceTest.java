package dev.requesttrace.observability.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.requesttrace.observability.config.MdcTaskDecorator;
import dev.requesttrace.observability.web.TraceIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncDemoServiceTest {

    private final ThreadPoolTaskExecutor noMdcExecutor = executor("test-no-mdc-", false);
    private final ThreadPoolTaskExecutor mdcExecutor = executor("test-mdc-", true);
    private final AsyncDemoService service = new AsyncDemoService(noMdcExecutor, mdcExecutor);

    @AfterEach
    void tearDown() {
        MDC.clear();
        noMdcExecutor.shutdown();
        mdcExecutor.shutdown();
    }

    @Test
    void losesMdcOnPlainExecutor() {
        String traceId = putTraceId();

        AsyncDemoService.AsyncTraceResponse response = service.lost().join();

        assertThat(response.callerTraceId()).isEqualTo(traceId);
        assertThat(response.workerTraceId()).isNull();
        assertThat(response.workerThread()).startsWith("test-no-mdc-");
    }

    @Test
    void propagatesMdcWithTaskDecorator() {
        String traceId = putTraceId();

        AsyncDemoService.AsyncTraceResponse response = service.propagated().join();

        assertThat(response.callerTraceId()).isEqualTo(traceId);
        assertThat(response.workerTraceId()).isEqualTo(traceId);
        assertThat(response.workerThread()).startsWith("test-mdc-");
    }

    private String putTraceId() {
        String traceId = UUID.randomUUID().toString();
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
        return traceId;
    }

    private ThreadPoolTaskExecutor executor(String prefix, boolean decorate) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix(prefix);
        if (decorate) {
            executor.setTaskDecorator(new MdcTaskDecorator());
        }
        executor.initialize();
        return executor;
    }
}

