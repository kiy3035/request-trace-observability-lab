package dev.requesttrace.observability.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        long startedAt = System.nanoTime();
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            log.atInfo()
                    .addKeyValue("layer", "HTTP")
                    .addKeyValue("event", "REQUEST_START")
                    .addKeyValue("httpMethod", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .log("HTTP request started");
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.atInfo()
                    .addKeyValue("layer", "HTTP")
                    .addKeyValue("event", "REQUEST_END")
                    .addKeyValue("httpMethod", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("elapsedMs", elapsedMs)
                    .log("HTTP request completed");
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(String candidate) {
        if (candidate != null) {
            try {
                UUID parsed = UUID.fromString(candidate);
                if (parsed.toString().equals(candidate.toLowerCase(Locale.ROOT))) {
                    return parsed.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid client input is replaced with a server-generated trace ID.
            }
        }
        return UUID.randomUUID().toString();
    }
}

