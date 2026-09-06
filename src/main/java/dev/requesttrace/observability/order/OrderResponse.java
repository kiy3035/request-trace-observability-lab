package dev.requesttrace.observability.order;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String productName,
        int quantity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProductName(),
                order.getQuantity(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}

