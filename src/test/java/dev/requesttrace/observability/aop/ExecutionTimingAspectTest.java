package dev.requesttrace.observability.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.requesttrace.observability.config.SlowThresholdProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ExecutionTimingAspectTest {

    @Test
    void measuresSlowServiceAndEmitsSlowEvent() throws Throwable {
        ExecutionTimingAspect aspect = new ExecutionTimingAspect(new SlowThresholdProperties(500, 5, 200));
        ProceedingJoinPoint joinPoint = joinPoint("slowService");
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(20);
            return "done";
        });

        List<ILoggingEvent> events = capture(() -> aspect.measureService(joinPoint));

        assertThat(events).extracting(this::eventName)
                .containsExactly("SERVICE_END", "SLOW_SERVICE");
        assertThat(keyValues(events.getFirst()))
                .containsEntry("layer", "SERVICE")
                .containsEntry("slow", true)
                .containsEntry("success", true);
        assertThat(((Number) keyValues(events.getFirst()).get("elapsedMs")).longValue()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void measuresControllerWithoutSlowServiceEvent() throws Throwable {
        ExecutionTimingAspect aspect = new ExecutionTimingAspect(new SlowThresholdProperties(500, 500, 200));
        ProceedingJoinPoint joinPoint = joinPoint("get");
        when(joinPoint.proceed()).thenReturn("done");

        List<ILoggingEvent> events = capture(() -> aspect.measureController(joinPoint));

        assertThat(events).extracting(this::eventName).containsExactly("CONTROLLER_END");
        assertThat(keyValues(events.getFirst())).containsEntry("layer", "CONTROLLER");
    }

    @Test
    void rethrowsOriginalFailureAndRecordsIt() throws Throwable {
        ExecutionTimingAspect aspect = new ExecutionTimingAspect(new SlowThresholdProperties(500, 500, 200));
        ProceedingJoinPoint joinPoint = joinPoint("get");
        IllegalStateException expected = new IllegalStateException("expected");
        when(joinPoint.proceed()).thenThrow(expected);

        Logger logger = (Logger) LoggerFactory.getLogger(ExecutionTimingAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> aspect.measureService(joinPoint)).isSameAs(expected);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(keyValues(appender.list.getFirst()))
                .containsEntry("success", false)
                .containsEntry("exception", "IllegalStateException");
    }

    private ProceedingJoinPoint joinPoint(String methodName) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("dev.requesttrace.observability.demo.DemoService");
        when(signature.getName()).thenReturn(methodName);
        return joinPoint;
    }

    private List<ILoggingEvent> capture(ThrowingCall call) throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(ExecutionTimingAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            call.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    private String eventName(ILoggingEvent event) {
        return String.valueOf(keyValues(event).get("event"));
    }

    private Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
        return values;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        Object run() throws Throwable;
    }
}
