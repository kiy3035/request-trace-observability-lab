package dev.requesttrace.observability.config;

import java.util.List;
import java.util.Locale;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqlQueryLoggingListener implements QueryExecutionListener {

    static final int MAX_SQL_LENGTH = 2_000;

    private static final Logger log = LoggerFactory.getLogger(SqlQueryLoggingListener.class);

    private final SlowThresholdProperties thresholds;

    public SqlQueryLoggingListener(SlowThresholdProperties thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
    }

    @Override
    public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
        long elapsedMs = executionInfo.getElapsedTime();
        boolean slow = elapsedMs >= thresholds.sqlMs();
        boolean success = executionInfo.isSuccess();
        String exception = executionInfo.getThrowable() == null
                ? null
                : executionInfo.getThrowable().getClass().getSimpleName();

        for (QueryInfo queryInfo : queryInfoList) {
            String sql = normalizeAndTruncate(queryInfo.getQuery());
            String operation = extractOperation(sql);
            log.atInfo()
                    .addKeyValue("layer", "SQL")
                    .addKeyValue("event", "SQL_EXECUTION")
                    .addKeyValue("sqlOperation", operation)
                    .addKeyValue("sql", sql)
                    .addKeyValue("elapsedMs", elapsedMs)
                    .addKeyValue("slow", slow)
                    .addKeyValue("success", success)
                    .addKeyValue("exception", exception)
                    .log("SQL execution completed");
            if (slow) {
                log.atWarn()
                        .addKeyValue("layer", "SQL")
                        .addKeyValue("event", "SLOW_SQL")
                        .addKeyValue("sqlOperation", operation)
                        .addKeyValue("sql", sql)
                        .addKeyValue("elapsedMs", elapsedMs)
                        .addKeyValue("slow", true)
                        .addKeyValue("success", success)
                        .addKeyValue("exception", exception)
                        .log("Slow SQL detected");
            }
        }
    }

    private String normalizeAndTruncate(String query) {
        String normalized = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_SQL_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SQL_LENGTH) + "...";
    }

    private String extractOperation(String sql) {
        if (sql.isBlank()) {
            return "UNKNOWN";
        }
        int separator = sql.indexOf(' ');
        String operation = separator < 0 ? sql : sql.substring(0, separator);
        return operation.toUpperCase(Locale.ROOT);
    }
}

