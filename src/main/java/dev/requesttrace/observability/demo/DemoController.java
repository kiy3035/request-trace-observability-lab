package dev.requesttrace.observability.demo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@Validated
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demoService;
    private final AsyncDemoService asyncDemoService;

    public DemoController(DemoService demoService, AsyncDemoService asyncDemoService) {
        this.demoService = demoService;
        this.asyncDemoService = asyncDemoService;
    }

    @GetMapping("/slow-service")
    public DelayResponse slowService(
            @RequestParam(defaultValue = "700") @Min(0) @Max(5000) long delayMs
    ) {
        return new DelayResponse(demoService.slowService(delayMs));
    }

    @GetMapping("/slow-sql")
    public DelayResponse slowSql(
            @RequestParam(defaultValue = "300") @Min(0) @Max(5000) long delayMs
    ) {
        return new DelayResponse(demoService.slowSql(delayMs));
    }

    @PostMapping("/async/lost")
    public AsyncDemoService.AsyncTraceResponse asyncLost() {
        return asyncDemoService.lost().join();
    }

    @PostMapping("/async/propagated")
    public AsyncDemoService.AsyncTraceResponse asyncPropagated() {
        return asyncDemoService.propagated().join();
    }

    @GetMapping("/error")
    public void error() {
        demoService.error();
    }

    public record DelayResponse(long requestedDelayMs) {
    }
}

