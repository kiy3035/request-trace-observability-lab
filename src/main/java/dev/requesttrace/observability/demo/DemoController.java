package dev.requesttrace.observability.demo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/slow-service")
    public DelayResponse slowService(
            @RequestParam(defaultValue = "700") @Min(0) @Max(5000) long delayMs
    ) {
        return new DelayResponse(demoService.slowService(delayMs));
    }

    public record DelayResponse(long requestedDelayMs) {
    }
}

