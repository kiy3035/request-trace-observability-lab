package dev.requesttrace.observability.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class DataSourceProxyConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource actualDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    DataSource dataSource(
            @Qualifier("actualDataSource") HikariDataSource actualDataSource,
            SqlQueryLoggingListener listener
    ) {
        return ProxyDataSourceBuilder.create(actualDataSource)
                .name("observedDataSource")
                .listener(listener)
                .build();
    }
}

