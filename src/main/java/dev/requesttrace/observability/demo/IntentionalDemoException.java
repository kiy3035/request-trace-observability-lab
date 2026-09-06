package dev.requesttrace.observability.demo;

public class IntentionalDemoException extends RuntimeException {

    public IntentionalDemoException() {
        super("Intentional demo error");
    }
}

