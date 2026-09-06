package dev.requesttrace.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RequestTraceObservabilityLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequestTraceObservabilityLabApplication.class, args);
    }
}

