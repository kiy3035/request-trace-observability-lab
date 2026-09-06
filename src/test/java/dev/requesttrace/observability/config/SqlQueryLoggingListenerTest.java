package dev.requesttrace.observability.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SqlQueryLoggingListenerTest {

    @Test
    void logsSqlExecutionWithoutBindParameters() {
        List<ILoggingEvent> events = invokeListener(12, true, null, "select * from orders where id=?", 200);

        assertThat(events).hasSize(1);
        assertThat(keyValues(events.getFirst()))
                .containsEntry("layer", "SQL")
                .containsEntry("event", "SQL_EXECUTION")
                .containsEntry("sqlOperation", "SELECT")
                .containsEntry("sql", "select * from orders where id=?")
                .containsEntry("elapsedMs", 12L)
                .containsEntry("slow", false)
                .containsEntry("success", true);
    }

    @Test
    void emitsSlowSqlWhenThresholdIsExceeded() {
        List<ILoggingEvent> events = invokeListener(250, true, null, "select pg_sleep(?)", 200);

        assertThat(events).extracting(event -> keyValues(event).get("event"))
                .containsExactly("SQL_EXECUTION", "SLOW_SQL");
        assertThat(keyValues(events.getLast())).containsEntry("slow", true);
    }

    @Test
    void recordsJdbcFailureWithoutSwallowingIt() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        List<ILoggingEvent> events = invokeListener(3, false, failure, "delete from orders where id=?", 200);

        assertThat(keyValues(events.getFirst()))
                .containsEntry("success", false)
                .containsEntry("exception", "IllegalStateException");
    }

    @Test
    void boundsSqlLogLength() {
        String longSql = "select " + "x".repeat(3_000);
        List<ILoggingEvent> events = invokeListener(1, true, null, longSql, 200);

        String loggedSql = (String) keyValues(events.getFirst()).get("sql");
        assertThat(loggedSql).hasSize(SqlQueryLoggingListener.MAX_SQL_LENGTH + 3).endsWith("...");
    }

    private List<ILoggingEvent> invokeListener(
            long elapsedMs,
            boolean success,
            Throwable failure,
            String sql,
            long thresholdMs
    ) {
        ExecutionInfo executionInfo = mock(ExecutionInfo.class);
        when(executionInfo.getElapsedTime()).thenReturn(elapsedMs);
        when(executionInfo.isSuccess()).thenReturn(success);
        when(executionInfo.getThrowable()).thenReturn(failure);
        QueryInfo queryInfo = mock(QueryInfo.class);
        when(queryInfo.getQuery()).thenReturn(sql);

        Logger logger = (Logger) LoggerFactory.getLogger(SqlQueryLoggingListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            SqlQueryLoggingListener listener = new SqlQueryLoggingListener(
                    new SlowThresholdProperties(500, 500, thresholdMs)
            );
            listener.afterQuery(executionInfo, List.of(queryInfo));
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    private Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
        return values;
    }
}

