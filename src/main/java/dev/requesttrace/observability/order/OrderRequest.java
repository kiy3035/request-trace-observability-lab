package dev.requesttrace.observability.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @NotBlank @Size(max = 100) String productName,
        @Min(1) int quantity
) {
}

