package dev.requesttrace.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.requesttrace.observability.config.SqlQueryLoggingListener;
import dev.requesttrace.observability.order.OrderRepository;
import dev.requesttrace.observability.web.TraceIdFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrderRepository orderRepository;

    @BeforeEach
    void cleanDatabase() {
        orderRepository.deleteAll();
    }

    @Test
    void createsOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"keyboard","quantity":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/orders/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productName").value("keyboard"))
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    void readsOrder() throws Exception {
        long id = createOrder("keyboard", 1);

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.productName").value("keyboard"))
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    void updatesOrder() throws Exception {
        long id = createOrder("keyboard", 1);

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":"mechanical keyboard","quantity":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("mechanical keyboard"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void deletesOrder() throws Exception {
        long id = createOrder("keyboard", 1);

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(orderRepository.findById(id)).isEmpty();
    }

    @Test
    void rejectsInvalidOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productName":" ","quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.productName").exists())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    void returnsNotFoundForMissingOrder() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found: 999999"));
    }

    @Test
    void sqlLogUsesRequestTraceIdAndOmitsBindValues() throws Exception {
        String traceId = UUID.randomUUID().toString();
        Logger logger = (Logger) LoggerFactory.getLogger(SqlQueryLoggingListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/orders")
                            .header(TraceIdFilter.TRACE_ID_HEADER, traceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"productName":"private-product-name","quantity":1}
                                    """))
                    .andExpect(status().isCreated());
        } finally {
            logger.detachAppender(appender);
        }

        ILoggingEvent insertLog = appender.list.stream()
                .filter(event -> "INSERT".equals(keyValues(event).get("sqlOperation")))
                .findFirst()
                .orElseThrow();
        assertThat(insertLog.getMDCPropertyMap()).containsEntry(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
        assertThat((String) keyValues(insertLog).get("sql")).doesNotContain("private-product-name");
    }

    private long createOrder(String productName, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBody(productName, quantity))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/orders/\\d+")))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private record CreateBody(String productName, int quantity) {
    }

    private Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
        return values;
    }
}
