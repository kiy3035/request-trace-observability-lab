package dev.requesttrace.observability.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SlowSqlDemoRepository {

    private final JdbcTemplate jdbcTemplate;

    public SlowSqlDemoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void sleep(long delayMs) {
        jdbcTemplate.queryForObject("select pg_sleep(?)", Object.class, delayMs / 1000.0);
    }
}

