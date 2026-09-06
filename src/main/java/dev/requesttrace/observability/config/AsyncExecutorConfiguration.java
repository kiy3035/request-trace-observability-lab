package dev.requesttrace.observability.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class AsyncExecutorConfiguration {

    @Bean(name = "noMdcExecutor")
    Executor noMdcExecutor() {
        return executor("async-no-mdc-", null);
    }

    @Bean(name = "mdcExecutor")
    Executor mdcExecutor() {
        return executor("async-mdc-", new MdcTaskDecorator());
    }

    private ThreadPoolTaskExecutor executor(String threadNamePrefix, MdcTaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix(threadNamePrefix);
        if (decorator != null) {
            executor.setTaskDecorator(decorator);
        }
        executor.initialize();
        return executor;
    }
}

