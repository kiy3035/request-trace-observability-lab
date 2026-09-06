package dev.requesttrace.observability.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.requesttrace.observability.config.SlowThresholdProperties;
import jakarta.servlet.ServletException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter(new SlowThresholdProperties(500, 500));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesTraceIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                traceIdInChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY))
        );

        String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(UUID.fromString(responseTraceId).toString()).isEqualTo(responseTraceId);
        assertThat(traceIdInChain).hasValue(responseTraceId);
    }

    @Test
    void reusesValidUuidHeader() throws Exception {
        String suppliedTraceId = UUID.randomUUID().toString();
        MockHttpServletRequest request = request();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, suppliedTraceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(suppliedTraceId);
    }

    @Test
    void replacesInvalidTraceIdHeader() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(responseTraceId).isNotEqualTo("not-a-uuid");
        assertThat(UUID.fromString(responseTraceId)).isNotNull();
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(request(), new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNotBlank()
        );

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcWhenChainThrows() {
        assertThatThrownBy(() -> filter.doFilter(
                request(),
                new MockHttpServletResponse(),
                (req, res) -> {
                    throw new ServletException("expected");
                }
        )).isInstanceOf(ServletException.class);

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void logsRequestStartAndEndWithTraceId() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .hasSize(2)
                .allMatch(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("HTTP request started", "HTTP request completed");
        assertThat(appender.list)
                .allMatch(event -> event.getMDCPropertyMap().containsKey(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/orders/1");
    }
}

