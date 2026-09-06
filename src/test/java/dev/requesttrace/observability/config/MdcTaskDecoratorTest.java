package dev.requesttrace.observability.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.requesttrace.observability.web.TraceIdFilter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesCallerMdcAndClearsWorkerAfterExecution() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String traceId = UUID.randomUUID().toString();
            MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
            Runnable decorated = new MdcTaskDecorator().decorate(() ->
                    assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isEqualTo(traceId)
            );

            executor.submit(decorated).get();
            Future<String> workerMdcAfterExecution = executor.submit(() ->
                    MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)
            );

            assertThat(workerMdcAfterExecution.get()).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void restoresWorkerContextAfterExecution() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "worker-before")).get();
            MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "caller");
            Runnable decorated = new MdcTaskDecorator().decorate(() ->
                    assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isEqualTo("caller")
            );

            executor.submit(decorated).get();

            assertThat(executor.submit(() -> MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).get())
                    .isEqualTo("worker-before");
        } finally {
            executor.submit(MDC::clear).get();
            executor.shutdownNow();
        }
    }
}

