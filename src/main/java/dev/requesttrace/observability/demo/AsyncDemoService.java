package dev.requesttrace.observability.demo;

import dev.requesttrace.observability.web.TraceIdFilter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AsyncDemoService {

    private static final Logger log = LoggerFactory.getLogger(AsyncDemoService.class);

    private final Executor noMdcExecutor;
    private final Executor mdcExecutor;

    public AsyncDemoService(
            @Qualifier("noMdcExecutor") Executor noMdcExecutor,
            @Qualifier("mdcExecutor") Executor mdcExecutor
    ) {
        this.noMdcExecutor = noMdcExecutor;
        this.mdcExecutor = mdcExecutor;
    }

    public CompletableFuture<AsyncTraceResponse> lost() {
        return execute("lost", noMdcExecutor);
    }

    public CompletableFuture<AsyncTraceResponse> propagated() {
        return execute("propagated", mdcExecutor);
    }

    private CompletableFuture<AsyncTraceResponse> execute(String mode, Executor executor) {
        String callerTraceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        log.atInfo()
                .addKeyValue("layer", "ASYNC")
                .addKeyValue("event", "ASYNC_CALLER")
                .addKeyValue("mode", mode)
                .log("Async task submitted");

        return CompletableFuture.supplyAsync(() -> {
            String workerTraceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
            log.atInfo()
                    .addKeyValue("layer", "ASYNC")
                    .addKeyValue("event", "ASYNC_WORKER")
                    .addKeyValue("mode", mode)
                    .addKeyValue("callerTraceId", callerTraceId)
                    .addKeyValue("workerTraceId", workerTraceId)
                    .log("Async task executed");
            return new AsyncTraceResponse(mode, callerTraceId, workerTraceId, Thread.currentThread().getName());
        }, executor);
    }

    public record AsyncTraceResponse(
            String mode,
            String callerTraceId,
            String workerTraceId,
            String workerThread
    ) {
    }
}

